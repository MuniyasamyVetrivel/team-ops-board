package com.teamops.marketing.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 12 end-to-end against MySQL: target types (seeded and custom), monthly targets with computed progress, the
 * per-type threshold, versioned and audited updates, one target per type and month, planned future months, closed
 * months, automatic actuals, the monthly bulk set and the month/quarter/year trend. Uses its own target types and a
 * throwaway department. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class TargetFlowIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private BusinessCalendar calendar;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private Long departmentId;

	private Long adminId;

	private String adminToken;

	private Long editorId;

	private String editorToken;

	private String managerToken;

	private String readerToken;

	private String nonce;

	private final List<Long> typeIds = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		nonce = Long.toHexString(System.nanoTime()).toUpperCase();
		String code = "TGT_" + nonce;
		departmentId = id(as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Targets %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated()));
		editorId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "TARGET_VIEW", "TARGET_EDIT",
				"SEO_VIEW", "SEO_EDIT");
		Long managerId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "MARKETING_EDIT",
				"TARGET_VIEW", "TARGET_EDIT");
		Long readerId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "TARGET_VIEW");
		editorToken = login(users.email(editorId));
		managerToken = login(users.email(managerId));
		readerToken = login(users.email(readerId));
	}

	@AfterEach
	void cleanUp() {
		for (Long typeId : typeIds) {
			jdbc.update("DELETE FROM marketing_targets WHERE target_type_id = ?", typeId);
			jdbc.update("DELETE FROM marketing_target_types WHERE id = ?", typeId);
		}
		jdbc.update("DELETE FROM marketing_pages WHERE department_id = ?", departmentId);
		jdbc.update("DELETE FROM audit_logs WHERE (action LIKE 'TARGET_%' OR action LIKE 'SEO_%') AND actor_id IN "
				+ "(SELECT id FROM users WHERE department_id = ? OR id = ?)", departmentId, adminId);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void seededTypesShowWhichActualsAreAutomatic() throws Exception {
		as(readerToken, get("/api/marketing/target-types")).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.code == 'KEYWORDS_TOP10')].automatic").value(Matchers.contains(true)))
			.andExpect(jsonPath("$[?(@.code == 'LANDING_PAGES_CREATED')].automatic").value(Matchers.contains(true)))
			// Leads (Phase 16) count automatically; backlinks arrive in Phase 17, so their actual is still entered by hand.
			.andExpect(jsonPath("$[?(@.code == 'WEBSITE_LEADS')].automatic").value(Matchers.contains(true)))
			.andExpect(jsonPath("$[?(@.code == 'BACKLINKS')].automatic").value(Matchers.contains(false)))
			.andExpect(jsonPath("$[?(@.code == 'ORGANIC_LEADS')].leadSourceFilter").value(Matchers.contains("ORGANIC")))
			.andExpect(jsonPath("$[?(@.code == 'MARKETING_PROSPECTS')].actualSource").value(Matchers.contains("MANUAL")));

		// Only marketing managers add types.
		as(editorToken, post("/api/marketing/target-types").contentType(MediaType.APPLICATION_JSON)
			.content(typeJson("Nope " + nonce, "MANUAL", null))).andExpect(status().isForbidden());
		as(managerToken, post("/api/marketing/target-types").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Leads %s\",\"unit\":\"COUNT\",\"actualSource\":\"LEADS_BY_SOURCE\"}".formatted(nonce)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("LEAD_SOURCE_REQUIRED"));
	}

	@Test
	void monthlyTargetsComputeProgressAndKeepHistory() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		Long leads = type("IT Website Leads " + nonce, "MANUAL", null);
		Long strict = type("IT Strict " + nonce, "MANUAL", "85");
		String strictCode = JsonPath.read(as(readerToken, get("/api/marketing/target-types")).andReturn()
			.getResponse()
			.getContentAsString(), "$[?(@.id == " + strict + ")].code").toString();
		assertThat(strictCode).contains("IT_STRICT_" + nonce);

		// 250 target, 200 actual → 80%, 50 remaining; in progress at the global 60%, behind at the type's 85%.
		Long leadsTarget = id(target(editorToken, leads, current, "250", "200").andExpect(status().isCreated())
			.andExpect(jsonPath("$.achievementPct").value(80.0))
			.andExpect(jsonPath("$.remaining").value(50.0))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.thresholdPct").value(60.0))
			.andExpect(jsonPath("$.actualOrigin").value("MANUAL"))
			.andExpect(jsonPath("$.editable").value(true))
			.andExpect(jsonPath("$.actualEditable").value(true)));
		target(editorToken, strict, current, "250", "200").andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("BEHIND"))
			.andExpect(jsonPath("$.thresholdPct").value(85.0));
		target(editorToken, leads, current, "300", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TARGET_EXISTS"));
		target(editorToken, leads, current.previous(), "10.5", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_VALUE"));
		target(readerToken, leads, current.previous(), "100", null).andExpect(status().isForbidden());

		// 250 target, 275 actual → 110%, nothing remaining; versioned and audited.
		as(editorToken, put("/api/marketing/targets/" + leadsTarget).contentType(MediaType.APPLICATION_JSON)
			.content(update(5, "250", "275"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(editorToken, put("/api/marketing/targets/" + leadsTarget).contentType(MediaType.APPLICATION_JSON)
			.content(update(0, "250", "275"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.achievementPct").value(110.0))
			.andExpect(jsonPath("$.remaining").value(0.0))
			.andExpect(jsonPath("$.status").value("ACHIEVED"));
		String details = jdbc.queryForObject(
				"SELECT details FROM audit_logs WHERE action = 'TARGET_UPDATED' AND entity_id = ?", String.class,
				leadsTarget);
		assertThat(details).containsPattern("\"actualValue\":\\s?\\{").containsPattern("\"from\":\\s?200")
			.containsPattern("\"to\":\\s?275");

		// The month's table, filtered by computed status.
		as(readerToken, get("/api/marketing/targets").param("status", "ACHIEVED")).andExpect(status().isOk())
			.andExpect(jsonPath("$.targets[?(@.id == " + leadsTarget + ")]").isNotEmpty())
			.andExpect(jsonPath("$.targets[?(@.type.id == " + strict + ")]").isEmpty())
			.andExpect(jsonPath("$.period.month").value(current.month()));

		// Next month is planned: set all at once, no actual or status yet, and no actuals allowed.
		MarketingPeriod next = current.next();
		as(editorToken, post("/api/marketing/targets/monthly").contentType(MediaType.APPLICATION_JSON)
			.content("{\"month\":%d,\"year\":%d,\"departmentId\":%d,\"entries\":[{\"typeId\":%d,\"targetValue\":260},{\"typeId\":%d,\"targetValue\":300}]}"
				.formatted(next.month(), next.year(), departmentId, leads, strict)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.created").value(2));
		as(editorToken, post("/api/marketing/targets/monthly").contentType(MediaType.APPLICATION_JSON)
			.content("{\"month\":%d,\"year\":%d,\"departmentId\":%d,\"entries\":[{\"typeId\":%d,\"targetValue\":1}]}"
				.formatted(next.month(), next.year(), departmentId, leads)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TARGET_EXISTS"));
		String planned = as(readerToken, get("/api/marketing/targets").param("month", String.valueOf(next.month()))
			.param("year", String.valueOf(next.year()))).andReturn().getResponse().getContentAsString();
		List<Object> plannedLeads = JsonPath.read(planned, "$.targets[?(@.type.id == " + leads + ")]");
		assertThat(plannedLeads).hasSize(1);
		assertThat(JsonPath.<List<Object>>read(planned, "$.targets[?(@.type.id == " + leads + ")].status")).containsExactly((Object) null);
		assertThat(JsonPath.<List<Object>>read(planned, "$.targets[?(@.type.id == " + leads + ")].remaining")).containsExactly(260.0);
		Long plannedId = ((Number) JsonPath.<List<Object>>read(planned, "$.targets[?(@.type.id == " + leads + ")].id").getFirst()).longValue();
		as(editorToken, put("/api/marketing/targets/" + plannedId).contentType(MediaType.APPLICATION_JSON)
			.content(update(0, "260", "10"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FUTURE_ACTUAL"));

		// Closed months are locked except to a Super Admin.
		MarketingPeriod closed = current.plusMonths(-3);
		target(editorToken, leads, closed, "200", "185").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));
		target(adminToken, leads, closed, "200", "185").andExpect(status().isCreated())
			.andExpect(jsonPath("$.editable").value(true));

		// Trend: months of the year, its quarters, and five years.
		as(readerToken, get("/api/marketing/targets/trend").param("typeId", leads.toString())).andExpect(status().isOk())
			.andExpect(jsonPath("$.points.length()").value(12))
			.andExpect(jsonPath("$.points[%d].targetValue".formatted(current.month() - 1)).value(250.0))
			.andExpect(jsonPath("$.points[%d].actual".formatted(current.month() - 1)).value(275.0))
			.andExpect(jsonPath("$.points[%d].status".formatted(current.month() - 1)).value("ACHIEVED"));
		as(readerToken, get("/api/marketing/targets/trend").param("typeId", leads.toString()).param("view", "YEAR"))
			.andExpect(jsonPath("$.points.length()").value(5))
			.andExpect(jsonPath("$.points[4].label").value(String.valueOf(current.year())));
		String quarters = as(readerToken, get("/api/marketing/targets/trend").param("typeId", leads.toString())
			.param("view", "QUARTER")
			.param("year", String.valueOf(current.year()))).andReturn().getResponse().getContentAsString();
		int quarter = current.quarter() - 1;
		assertThat(JsonPath.<String>read(quarters, "$.points[" + quarter + "].label")).isEqualTo("Q" + current.quarter() + " " + current.year());
		int months = JsonPath.read(quarters, "$.points[" + quarter + "].months");
		assertThat(months).isGreaterThanOrEqualTo(1);

		// A type in use keeps its unit and source; it is deactivated, not deleted.
		as(managerToken, put("/api/marketing/target-types/" + leads).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"name\":\"IT Website Leads %s\",\"unit\":\"CURRENCY\",\"actualSource\":\"MANUAL\",\"active\":true,\"position\":99}".formatted(nonce)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TYPE_IN_USE"));
		as(managerToken, delete("/api/marketing/target-types/" + leads)).andExpect(status().isConflict());
		as(managerToken, put("/api/marketing/target-types/" + leads).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"name\":\"IT Website Leads %s\",\"unit\":\"COUNT\",\"actualSource\":\"MANUAL\",\"active\":false,\"position\":99}".formatted(nonce)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false))
			.andExpect(jsonPath("$.locked").value(true));
		target(editorToken, leads, current.previous(), "100", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TYPE_INACTIVE"));
	}

	@Test
	void automaticActualsComeFromTheRecords() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		Long landing = type("IT Landing Pages " + nonce, "LANDING_PAGES", null);
		as(readerToken, get("/api/marketing/targets")).andExpect(jsonPath("$.typesWithoutTarget[?(@.id == " + landing + ")].automatic")
			.value(Matchers.contains(true)));

		// A landing page created today counts towards this month.
		as(editorToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"url\":\"/landing/it-%s\",\"title\":\"IT landing\",\"pageType\":\"LANDING_PAGE\",\"departmentId\":%d}"
				.formatted(nonce.toLowerCase(), departmentId)))
			.andExpect(status().isCreated());
		String created = target(editorToken, landing, current, "2", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.actualOrigin").value("AUTOMATIC"))
			.andExpect(jsonPath("$.actualEditable").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(((Number) JsonPath.read(created, "$.actual")).intValue()).isGreaterThanOrEqualTo(1);

		// Its actual cannot be typed in.
		target(editorToken, landing, current.previous(), "2", "1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ACTUAL_IS_AUTOMATIC"));
	}

	private Long type(String name, String source, String threshold) throws Exception {
		Long id = id(as(managerToken, post("/api/marketing/target-types").contentType(MediaType.APPLICATION_JSON)
			.content(typeJson(name, source, threshold))).andExpect(status().isCreated()));
		typeIds.add(id);
		return id;
	}

	private static String typeJson(String name, String source, String threshold) {
		return "{\"name\":\"%s\",\"unit\":\"COUNT\",\"actualSource\":\"%s\",\"behindThresholdPct\":%s}".formatted(name,
				source, threshold);
	}

	private ResultActions target(String token, Long typeId, MarketingPeriod period, String target, String actual)
			throws Exception {
		return as(token, post("/api/marketing/targets").contentType(MediaType.APPLICATION_JSON)
			.content("{\"typeId\":%d,\"month\":%d,\"year\":%d,\"targetValue\":%s,\"actualValue\":%s,\"ownerId\":%d,\"departmentId\":%d}"
				.formatted(typeId, period.month(), period.year(), target, actual, editorId, departmentId)));
	}

	private String update(int version, String target, String actual) {
		return "{\"version\":%d,\"targetValue\":%s,\"actualValue\":%s,\"ownerId\":%d,\"departmentId\":%d}".formatted(version,
				target, actual, editorId, departmentId);
	}

	private static Long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private ResultActions as(String token, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String login(String email) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, IntegrationUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

}
