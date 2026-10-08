package com.teamops.marketing.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.repository.SeoKeywordRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 11 end-to-end against MySQL: insert-only monthly rankings, the unique month per keyword, corrections of open
 * months (versioned and audited) and locked older months, snapshots and the keyword cache, the monthly update, the
 * ranking table filters and sorts, the monthly report, history, CSV export and CSV import. Runs in a throwaway
 * department. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class RankingFlowIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private SeoKeywordRepository keywordRepository;

	@Autowired
	private BusinessCalendar calendar;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private Long departmentId;

	private Long editorId;

	private String editorToken;

	private String readerToken;

	private String url;

	private Long pageId;

	private Long desktopId;

	private Long mobileId;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		String nonce = Long.toHexString(System.nanoTime()).toUpperCase();
		String code = "RNK_" + nonce;
		departmentId = id(as(login(users.email(adminId)), post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Rankings %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated()));
		editorId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT");
		Long readerId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "SEO_VIEW");
		editorToken = login(users.email(editorId));
		readerToken = login(users.email(readerId));

		url = "/it-rankings/" + nonce.toLowerCase();
		pageId = id(as(editorToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"url\":\"%s\",\"title\":\"SAP Testing Services\",\"pageType\":\"SERVICE\",\"departmentId\":%d}"
				.formatted(url, departmentId)))
			.andExpect(status().isCreated()));
		desktopId = keyword("SAP Testing Services", "DESKTOP");
		mobileId = keyword("SAP Testing Services", "MOBILE");
	}

	@AfterEach
	void cleanUp() {
		String pages = "SELECT id FROM marketing_pages WHERE department_id = ?";
		jdbc.update("DELETE FROM keyword_ranking_history WHERE page_id IN (SELECT id FROM (" + pages + ") p)",
				departmentId);
		jdbc.update("DELETE FROM marketing_keywords WHERE page_id IN (SELECT id FROM (" + pages + ") p)", departmentId);
		jdbc.update("DELETE FROM marketing_pages WHERE department_id = ?", departmentId);
		jdbc.update("DELETE FROM audit_logs WHERE (action LIKE 'SEO_%' OR action = 'CSV_IMPORTED') AND actor_id = ?",
				editorId);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void monthsAreInsertOnlyAndCorrectionsAreAudited() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		MarketingPeriod previous = current.previous();
		MarketingPeriod older = previous.previous();

		// Last month, then this month for both keywords in one monthly update.
		Long previousId = ((Number) JsonPath.read(record(desktopId, previous, "12").andExpect(status().isCreated())
			.andExpect(jsonPath("$.entries[0].position").value(12))
			.andExpect(jsonPath("$.entries[0].status").value("RANKING"))
			.andExpect(jsonPath("$.entries[0].change.movement").value("NEW"))
			.andExpect(jsonPath("$.entries[0].correctable").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.entries[0].id")).longValue();
		record(desktopId, previous, "9").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RANKING_EXISTS"));
		record(desktopId, current.next(), "5").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FUTURE_PERIOD"));
		as(readerToken, post("/api/marketing/keywords/" + desktopId + "/rankings").contentType(MediaType.APPLICATION_JSON)
			.content(rankingJson(current, "7"))).andExpect(status().isForbidden());

		as(editorToken, post("/api/marketing/rankings/monthly").contentType(MediaType.APPLICATION_JSON)
			.content(monthly(previous, Map.of(mobileId, "30")))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.recorded").value(1));
		as(editorToken, post("/api/marketing/rankings/monthly").contentType(MediaType.APPLICATION_JSON)
			.content(monthly(current, Map.of(desktopId, "7", mobileId, "null")))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.recorded").value(2));
		// The whole update is refused when any keyword already has the month.
		as(editorToken, post("/api/marketing/rankings/monthly").contentType(MediaType.APPLICATION_JSON)
			.content(monthly(current, Map.of(desktopId, "6")))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RANKING_EXISTS"));

		as(readerToken, get("/api/marketing/keywords/" + desktopId + "/rankings")).andExpect(status().isOk())
			.andExpect(jsonPath("$.entries.length()").value(2))
			.andExpect(jsonPath("$.entries[0].position").value(7))
			.andExpect(jsonPath("$.entries[0].status").value("TOP_10"))
			.andExpect(jsonPath("$.entries[0].change.value").value(5))
			.andExpect(jsonPath("$.entries[0].change.movement").value("IMPROVED"));
		SeoKeyword cached = keywordRepository.findById(desktopId).orElseThrow();
		assertThat(cached.getCurrentPosition()).isEqualTo(7);
		assertThat(cached.getPreviousPosition()).isEqualTo(12);

		// Backfilling an older month updates the next month's snapshot, never its position.
		record(desktopId, older, "20").andExpect(status().isCreated());
		assertThat(snapshot(desktopId, previous)).containsExactly(12, 20, 8);

		// Corrections: versioned, audited, and the next month's snapshot follows.
		as(editorToken, put("/api/marketing/rankings/" + previousId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":99,\"position\":10}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		int version = jdbc.queryForObject("SELECT version FROM keyword_ranking_history WHERE id = ?", Integer.class,
				previousId);
		as(editorToken, put("/api/marketing/rankings/" + previousId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":%d,\"position\":10,\"notes\":\"Typo in the report\"}".formatted(version)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.entries[1].position").value(10))
			.andExpect(jsonPath("$.entries[1].notes").value("Typo in the report"))
			.andExpect(jsonPath("$.entries[0].change.value").value(3));
		assertThat(snapshot(desktopId, previous)).containsExactly(10, 20, 10);
		assertThat(snapshot(desktopId, current)).containsExactly(7, 10, 3);
		assertThat(keywordRepository.findById(desktopId).orElseThrow().getPreviousPosition()).isEqualTo(10);
		String details = jdbc.queryForObject(
				"SELECT details FROM audit_logs WHERE action = 'SEO_RANKING_CORRECTED' AND entity_id = ?", String.class,
				previousId);
		// MySQL normalises JSON spacing, so match loosely.
		assertThat(details).containsPattern("\"position\":\\s?\\{").containsPattern("\"from\":\\s?12")
			.containsPattern("\"to\":\\s?10");
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_logs WHERE action = 'SEO_RANKING_RECORDED' AND actor_id = ?", Integer.class,
				editorId))
			.isEqualTo(5);

		// Months before last are closed.
		Long olderId = jdbc.queryForObject(
				"SELECT id FROM keyword_ranking_history WHERE keyword_id = ? AND ranking_month = ? AND ranking_year = ?",
				Long.class, desktopId, older.month(), older.year());
		as(editorToken, put("/api/marketing/rankings/" + olderId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"position\":19}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));
	}

	@Test
	void tableReportHistoryAndExportFollowTheMonth() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		MarketingPeriod previous = current.previous();
		as(editorToken, post("/api/marketing/rankings/monthly").contentType(MediaType.APPLICATION_JSON)
			.content(monthly(previous, Map.of(desktopId, "12", mobileId, "30")))).andExpect(status().isCreated());
		as(editorToken, post("/api/marketing/rankings/monthly").contentType(MediaType.APPLICATION_JSON)
			.content(monthly(current, Map.of(desktopId, "7", mobileId, "null")))).andExpect(status().isCreated());

		// Best ranking first; the month's row is included so it can be corrected.
		as(readerToken, get("/api/marketing/rankings").param("pageId", pageId.toString()).param("sort", "best"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].id").value(desktopId))
			.andExpect(jsonPath("$.content[0].ranking.position").value(7))
			.andExpect(jsonPath("$.content[0].entry.id").isNumber())
			.andExpect(jsonPath("$.content[1].ranking.status").value("NOT_RANKED"))
			.andExpect(jsonPath("$.content[1].ranking.change.movement").value("DECLINED"));
		as(readerToken, get("/api/marketing/rankings").param("pageId", pageId.toString()).param("sort", "worst"))
			.andExpect(jsonPath("$.content[0].id").value(mobileId));
		as(readerToken, get("/api/marketing/rankings").param("pageId", pageId.toString()).param("movement", "DECLINED"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(mobileId));
		as(readerToken, get("/api/marketing/rankings").param("pageId", pageId.toString()).param("standing", "TOP_10"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(desktopId));
		as(readerToken, get("/api/marketing/rankings").param("pageId", pageId.toString())
			.param("minPosition", "20")
			.param("maxPosition", "40")
			.param("month", String.valueOf(previous.month()))
			.param("year", String.valueOf(previous.year()))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].ranking.position").value(30));

		// Monthly report: bands for this month and last month.
		as(readerToken, get("/api/marketing/rankings/monthly").param("pageId", pageId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stats.totalKeywords").value(2))
			.andExpect(jsonPath("$.stats.top10").value(1))
			.andExpect(jsonPath("$.stats.notRanked").value(1))
			.andExpect(jsonPath("$.previousStats.positions11to20").value(1))
			.andExpect(jsonPath("$.previousStats.positions21to50").value(1))
			.andExpect(jsonPath("$.previousPeriod.month").value(previous.month()));

		// Page history for the line chart, oldest month first.
		as(readerToken, get("/api/marketing/pages/" + pageId + "/rankings").param("months", "3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.periods.length()").value(3))
			.andExpect(jsonPath("$.periods[2].month").value(current.month()))
			.andExpect(jsonPath("$.series.length()").value(2))
			.andExpect(jsonPath("$.series[?(@.device == 'DESKTOP')].points[2].position").value(Matchers.contains(7)))
			.andExpect(jsonPath("$.series[?(@.device == 'MOBILE')].points[2].recorded").value(Matchers.contains(true)))
			.andExpect(jsonPath("$.series[?(@.device == 'MOBILE')].points[0].recorded").value(Matchers.contains(false)))
			.andExpect(jsonPath("$.averages[1]").value(21.0))
			.andExpect(jsonPath("$.averages[2]").value(7.0));

		String csv = as(readerToken, get("/api/marketing/rankings/export").param("pageId", pageId.toString()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/csv"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		List<String> lines = csv.lines().toList();
		assertThat(lines).hasSize(3);
		assertThat(lines.get(0)).contains("Page,Page URL,Keyword");
		assertThat(lines.get(1)).contains(url, "DESKTOP", ",7,12,5,TOP 10,");
		assertThat(lines.get(2)).contains("MOBILE", ",NR,30,DECLINED,NOT RANKED,");
	}

	@Test
	void csvImportAddsNewMonthsOnlyAfterAPreview() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		MarketingPeriod old = current.plusMonths(-3);
		record(desktopId, current, "7").andExpect(status().isCreated());

		String file = """
				page_url,keyword,month,year,position,search_volume,device
				%1$s,sap testing services,%2$d,%3$d,45,880,MOBILE
				%1$s,SAP Testing Services,%4$d,%5$d,6,,DESKTOP
				%1$s,Unknown keyword,%2$d,%3$d,10,,DESKTOP
				%1$s,SAP Testing Services,%6$d,%7$d,4,,DESKTOP
				%1$s,SAP Testing Services,%2$d,%3$d,,,DESKTOP
				""".formatted(url, old.month(), old.year(), current.month(), current.year(), current.next().month(),
				current.next().year());
		MockMultipartFile upload = new MockMultipartFile("file", "rankings.csv", "text/csv",
				file.getBytes(StandardCharsets.UTF_8));

		as(readerToken, multipart("/api/marketing/imports/seo-rankings/preview").file(upload))
			.andExpect(status().isForbidden());
		String preview = as(editorToken, multipart("/api/marketing/imports/seo-rankings/preview").file(upload))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(4))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message", Matchers.containsString("already recorded")))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].column").value("keyword"))
			.andExpect(jsonPath("$.invalidRows[2].errors[0].message").value("Future months cannot be recorded"))
			.andExpect(jsonPath("$.invalidRows[3].errors[0].column").value("position"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM keyword_ranking_history WHERE keyword_id = ?",
				Integer.class, mobileId))
			.isZero();

		as(editorToken, multipart("/api/marketing/imports/seo-rankings/commit").file(upload)
			.param("checksum", (String) JsonPath.read(preview, "$.checksum"))
			.param("skipInvalid", "true")).andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(1))
			.andExpect(jsonPath("$.skipped").value(4));
		as(readerToken, get("/api/marketing/keywords/" + mobileId + "/rankings"))
			.andExpect(jsonPath("$.entries.length()").value(1))
			.andExpect(jsonPath("$.entries[0].position").value(45))
			.andExpect(jsonPath("$.entries[0].searchVolume").value(880))
			.andExpect(jsonPath("$.entries[0].source").value("CSV"))
			.andExpect(jsonPath("$.entries[0].correctable").value(false));
		assertThat(keywordRepository.findById(mobileId).orElseThrow().getCurrentPosition()).isEqualTo(45);
	}

	/** [position, previous_position, ranking_change] of a month's row. */
	private List<Integer> snapshot(Long keywordId, MarketingPeriod period) {
		return jdbc.queryForObject("""
				SELECT ranking_position, previous_position, ranking_change FROM keyword_ranking_history
				WHERE keyword_id = ? AND ranking_month = ? AND ranking_year = ?
				""", (rs, i) -> java.util.Arrays.asList((Integer) rs.getObject(1, Integer.class),
				(Integer) rs.getObject(2, Integer.class), (Integer) rs.getObject(3, Integer.class)), keywordId,
				period.month(), period.year());
	}

	private ResultActions record(Long keywordId, MarketingPeriod period, String position) throws Exception {
		return as(editorToken, post("/api/marketing/keywords/" + keywordId + "/rankings")
			.contentType(MediaType.APPLICATION_JSON)
			.content(rankingJson(period, position)));
	}

	private static String rankingJson(MarketingPeriod period, String position) {
		return "{\"month\":%d,\"year\":%d,\"position\":%s,\"searchVolume\":1900}".formatted(period.month(),
				period.year(), position);
	}

	private static String monthly(MarketingPeriod period, Map<Long, String> positions) {
		String entries = positions.entrySet()
			.stream()
			.map(e -> "{\"keywordId\":%d,\"position\":%s}".formatted(e.getKey(), e.getValue()))
			.reduce((a, b) -> a + "," + b)
			.orElse("");
		return "{\"month\":%d,\"year\":%d,\"entries\":[%s]}".formatted(period.month(), period.year(), entries);
	}

	private Long keyword(String text, String device) throws Exception {
		return id(as(editorToken, post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content("{\"pageId\":%d,\"keyword\":\"%s\",\"device\":\"%s\",\"searchVolume\":1900}".formatted(pageId,
					text, device)))
			.andExpect(status().isCreated()));
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
