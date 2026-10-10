package com.teamops.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.support.SqlCounter;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Query budget for every dashboard, report, list and detail read (Phase 24). As a Super Admin, over whatever the
 * database holds (the dev seed when present), each endpoint must answer with a bounded number of SQL statements, never
 * run one statement more than {@link #MAX_REPEATS} times (an N+1), and never scan a table that grows with history
 * without any usable index ({@code EXPLAIN}). Writes {@code target/query-report.txt}. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SqlCounter.Config.class)
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class QueryBudgetIT {

	/** The month against the month before, plus target actuals for both months, each one grouped query. */
	private static final int MAX_REPEATS = 3;

	private static final int DEFAULT_BUDGET = 20;

	/** Composite reads: one grouped query per section, independent of the number of rows. */
	private static final Map<String, Integer> BUDGETS = Map.of("/api/dashboard", 25, "/api/marketing/dashboard", 30,
			"/api/marketing/reports/monthly", 45, "/api/search?q=sap", 25);

	/** Tables that grow with every month of use; small lookup tables (departments, SLA policies) may be scanned. */
	private static final Set<String> GROWING = Set.of("tasks", "tickets", "projects", "approvals", "audit_logs",
			"notifications", "marketing_leads", "backlinks", "content_items", "keyword_ranking_history",
			"marketing_activity_occurrences", "task_history", "ticket_history");

	private static final List<String> PATHS = List.of("/api/dashboard", "/api/workload", "/api/reports/tasks",
			"/api/reports/workload", "/api/reports/tickets", "/api/reports/projects", "/api/marketing/dashboard",
			"/api/marketing/reports/monthly", "/api/tasks", "/api/tickets", "/api/projects", "/api/approvals", "/api/team",
			"/api/users", "/api/departments", "/api/sla/summary", "/api/marketing/rankings",
			"/api/marketing/rankings/monthly", "/api/marketing/pages", "/api/marketing/keywords", "/api/marketing/targets",
			"/api/marketing/activities", "/api/marketing/activity-occurrences", "/api/marketing/email-campaigns",
			"/api/marketing/paid-campaigns", "/api/marketing/leads", "/api/marketing/backlinks", "/api/marketing/content",
			"/api/announcements", "/api/knowledge-base/articles", "/api/documents", "/api/notifications",
			"/api/admin/audit-logs", "/api/search?q=sap", "/api/calendar?from=2026-10-01&to=2026-10-31");

	/** Detail pages, opened on the first row of their list when the list has one. */
	private static final Map<String, String> DETAILS = Map.of("/api/team/", "/api/team", "/api/tasks/", "/api/tasks",
			"/api/tickets/", "/api/tickets", "/api/projects/", "/api/projects", "/api/marketing/pages/",
			"/api/marketing/pages");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private String token;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(users.email(adminId), IntegrationUsers.PASSWORD)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		token = JsonPath.read(body, "$.accessToken");
	}

	@AfterEach
	void cleanUp() {
		users.deleteAll();
	}

	private record Measure(String path, int status, List<String> statements, List<String> rendered, long millis) {
	}

	private Measure measure(String path) throws Exception {
		SqlCounter.start();
		long started = System.nanoTime();
		MvcResult result = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
		long millis = (System.nanoTime() - started) / 1_000_000;
		List<String> statements = SqlCounter.stop();
		return new Measure(path, result.getResponse().getStatus(), statements, SqlCounter.rendered(), millis);
	}

	private String firstId(String path) throws Exception {
		String body = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Object> ids = JsonPath.read(body, "$.content[*].id");
		return ids.isEmpty() ? null : ids.getFirst().toString();
	}

	/**
	 * Tables of a growing kind that the plan reads in full without any index it could use. A page ({@code limit}) in
	 * the order of an indexed column is fine: on small tables MySQL prefers a quick sort, on large ones it walks the
	 * index and stops after the page.
	 */
	private List<String> unindexedScans(String sql) {
		List<String> scans = new ArrayList<>();
		for (Map<String, Object> row : jdbc.queryForList("explain " + sql)) {
			String alias = String.valueOf(row.get("table"));
			if (!"ALL".equals(row.get("type")) || row.get("possible_keys") != null) {
				continue;
			}
			GROWING.stream()
				.filter(table -> sql.matches("(?is).*\\b(from|join)\\s+" + table + "\\s+(as\\s+)?" + alias + "\\b.*"))
				.findFirst()
				.filter(table -> !pagedByIndexedColumn(sql, table, alias))
				.ifPresent(table -> scans.add(table + " (" + row.get("rows") + " rows)"));
		}
		return scans;
	}

	private boolean pagedByIndexedColumn(String sql, String table, String alias) {
		Matcher order = Pattern
			.compile("(?is)\\border by\\s+" + Pattern.quote(alias) + "\\.(\\w+)\\b.*\\blimit\\b")
			.matcher(sql);
		return order.find() && jdbc.queryForObject("""
				select count(*) from information_schema.statistics
				where table_schema = database() and table_name = ? and column_name = ? and seq_in_index = 1
				""", Integer.class, table, order.group(1)) > 0;
	}

	@Test
	void readsStayWithinTheirQueryBudget() throws Exception {
		List<String> paths = new ArrayList<>(PATHS);
		for (Map.Entry<String, String> detail : new LinkedHashMap<>(DETAILS).entrySet()) {
			String id = firstId(detail.getValue());
			if (id != null) {
				paths.add(detail.getKey() + id);
			}
		}

		StringBuilder report = new StringBuilder();
		List<String> problems = new ArrayList<>();
		Set<String> explained = new HashSet<>();
		for (String path : paths) {
			measure(path); // warm-up: settings and other per-process caches
			Measure m = measure(path);
			long repeats = SqlCounter.maxRepeats(m.statements());
			int budget = BUDGETS.getOrDefault(path, DEFAULT_BUDGET);
			report.append("%-45s %3d  statements=%3d/%-3d maxRepeat=%d  %4d ms%n".formatted(path, m.status(),
					m.statements().size(), budget, repeats, m.millis()));
			if (m.status() != 200) {
				problems.add(path + " answered " + m.status());
			}
			if (m.statements().size() > budget) {
				problems.add(path + " ran " + m.statements().size() + " statements (budget " + budget + ")");
			}
			SqlCounter.repeated(m.statements(), MAX_REPEATS + 1)
				.forEach((sql, n) -> problems.add(path + " ran one statement " + n + " times (N+1?): " + sql));
			for (String sql : m.rendered()) {
				if (sql.regionMatches(true, 0, "select", 0, 6) && explained.add(sql)) {
					unindexedScans(sql).forEach(scan -> problems.add(path + " scans " + scan + " without an index: " + sql));
				}
			}
		}
		Files.writeString(Path.of("target", "query-report.txt"), report + "\n" + String.join("\n", problems));
		assertThat(problems).isEmpty();
	}

}
