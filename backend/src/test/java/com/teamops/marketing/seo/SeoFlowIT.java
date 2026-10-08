package com.teamops.marketing.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.seo.entity.KeywordRanking;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.repository.KeywordRankingRepository;
import com.teamops.marketing.seo.repository.SeoKeywordRepository;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 10 end-to-end against MySQL: SEO page and keyword CRUD, permission split (SEO_VIEW / SEO_EDIT), uniqueness,
 * optimistic locking, history-preserving deletes, and page statistics computed from the ranking history for the
 * selected month. Runs in a throwaway department. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class SeoFlowIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private SeoPageRepository pageRepository;

	@Autowired
	private SeoKeywordRepository keywordRepository;

	@Autowired
	private KeywordRankingRepository rankingRepository;

	@Autowired
	private BusinessCalendar calendar;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactions;

	private IntegrationUsers users;

	private Long departmentId;

	private Long editorId;

	private String editorToken;

	private String readerToken;

	private Long outsiderId;

	private String nonce;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		nonce = Long.toHexString(System.nanoTime()).toUpperCase();
		String code = "SEO_" + nonce;
		String body = as(login(users.email(adminId)), post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"SEO %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();

		editorId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT");
		Long readerId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "SEO_VIEW");
		outsiderId = users.create(departmentId, RoleCodes.EMPLOYEE);
		editorToken = login(users.email(editorId));
		readerToken = login(users.email(readerId));
	}

	@AfterEach
	void cleanUp() {
		String pages = "SELECT id FROM marketing_pages WHERE department_id = ?";
		jdbc.update("DELETE FROM keyword_ranking_history WHERE page_id IN (SELECT id FROM (" + pages + ") p)",
				departmentId);
		jdbc.update("DELETE FROM marketing_keywords WHERE page_id IN (SELECT id FROM (" + pages + ") p)", departmentId);
		jdbc.update("DELETE FROM marketing_pages WHERE department_id = ?", departmentId);
		jdbc.update("DELETE FROM audit_logs WHERE action LIKE 'SEO_%' AND actor_id = ?", editorId);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void pagesAndKeywordsWithStatisticsFromTheHistory() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		MarketingPeriod previous = current.previous();
		String url = "/it-seo/" + nonce.toLowerCase();

		Long pageId = id(as(editorToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content(page(url, "SAP Testing Services", editorId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.stats.totalKeywords").value(0))
			.andExpect(jsonPath("$.stats.averagePosition").doesNotExist())
			.andExpect(jsonPath("$.period.month").value(current.month()))
			.andExpect(jsonPath("$.permissions.canEdit").value(true)));

		// URLs are unique regardless of case; owners must be Digital Marketing users.
		as(editorToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content(page(url.toUpperCase(), "Copy", null))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_URL"));
		as(editorToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content(page(url + "-2", "Other", outsiderId))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_OWNER"));

		// Keywords are normalised, default to Google / India / desktop, and are unique per engine, location, device.
		Long desktopId = id(as(editorToken, post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content(keyword(pageId, "  SAP   Testing Services ", null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.keyword").value("SAP Testing Services"))
			.andExpect(jsonPath("$.searchEngine").value("GOOGLE"))
			.andExpect(jsonPath("$.location").value("India"))
			.andExpect(jsonPath("$.device").value("DESKTOP"))
			.andExpect(jsonPath("$.ranking.recorded").value(false))
			.andExpect(jsonPath("$.ranking.status").value("NOT_RANKED")));
		as(editorToken, post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content(keyword(pageId, "sap testing services", null))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_KEYWORD"));
		Long mobileId = id(as(editorToken, post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content(keyword(pageId, "SAP Testing Services", "MOBILE"))).andExpect(status().isCreated()));

		// Readers see everything but cannot change it.
		as(readerToken, get("/api/marketing/pages/" + pageId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions.canEdit").value(false));
		as(readerToken, post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content(keyword(pageId, "other", null))).andExpect(status().isForbidden());

		// History (recorded through the API from Phase 11): desktop 12 → 7, mobile 20 → Not Ranked.
		record(desktopId, previous, 12);
		record(desktopId, current, 7);
		record(mobileId, previous, 20);
		record(mobileId, current, null);

		as(readerToken, get("/api/marketing/pages/" + pageId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.keywordCount").value(2))
			.andExpect(jsonPath("$.stats.totalKeywords").value(2))
			.andExpect(jsonPath("$.stats.top10").value(1))
			.andExpect(jsonPath("$.stats.notRanked").value(1))
			.andExpect(jsonPath("$.stats.notRecorded").value(0))
			.andExpect(jsonPath("$.stats.improved").value(1))
			.andExpect(jsonPath("$.stats.declined").value(1))
			.andExpect(jsonPath("$.stats.averagePosition").value(7.0))
			.andExpect(jsonPath("$.previousPeriod.month").value(previous.month()))
			.andExpect(jsonPath("$.previousStats.top10").value(0))
			.andExpect(jsonPath("$.previousStats.ranking").value(2))
			.andExpect(jsonPath("$.previousStats.averagePosition").value(16.0));

		// The keyword list carries each keyword's standing for the selected month.
		as(readerToken, get("/api/marketing/keywords").param("pageId", pageId.toString())
			.param("device", "DESKTOP")
			.param("month", String.valueOf(current.month()))
			.param("year", String.valueOf(current.year()))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].ranking.position").value(7))
			.andExpect(jsonPath("$.content[0].ranking.status").value("TOP_10"))
			.andExpect(jsonPath("$.content[0].ranking.previousPosition").value(12))
			.andExpect(jsonPath("$.content[0].ranking.change.value").value(5))
			.andExpect(jsonPath("$.content[0].ranking.change.movement").value("IMPROVED"));
		as(readerToken, get("/api/marketing/keywords/" + mobileId).param("month", String.valueOf(previous.month()))
			.param("year", String.valueOf(previous.year()))).andExpect(status().isOk())
			.andExpect(jsonPath("$.ranking.position").value(20))
			.andExpect(jsonPath("$.ranking.status").value("RANKING"))
			.andExpect(jsonPath("$.ranking.change.movement").value("NEW"));
		as(readerToken, get("/api/marketing/pages").param("departmentId", departmentId.toString()))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].stats.top10").value(1))
			.andExpect(jsonPath("$.content[0].owner.id").value(editorId));

		// The keyword cache follows the latest two history rows.
		transactions.executeWithoutResult(status -> keywordRepository.refreshRankingCache(desktopId));
		SeoKeyword cached = keywordRepository.findById(desktopId).orElseThrow();
		assertThat(cached.getCurrentPosition()).isEqualTo(7);
		assertThat(cached.getPreviousPosition()).isEqualTo(12);
		assertThat(cached.getLastRankedAt()).isNotNull();

		// History is never deleted: keywords with history and pages with keywords can only be archived.
		as(editorToken, delete("/api/marketing/keywords/" + desktopId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("KEYWORD_HAS_HISTORY"));
		as(editorToken, delete("/api/marketing/pages/" + pageId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAGE_HAS_KEYWORDS"));
	}

	@Test
	void updatesAreVersionedAuditedAndArchivedPagesTakeNoKeywords() throws Exception {
		String url = "/it-seo/" + nonce.toLowerCase() + "/edit";
		String created = as(editorToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content(page(url, "Draft title", null))).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long pageId = ((Number) JsonPath.read(created, "$.id")).longValue();
		int version = JsonPath.read(created, "$.version");

		String update = """
				{"version":%d,"url":"%s","title":"Final title","pageType":"LANDING_PAGE","departmentId":%d,
				 "ownerId":%d,"status":"ARCHIVED"}
				""";
		as(editorToken, put("/api/marketing/pages/" + pageId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(version + 1, url, departmentId, editorId))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(editorToken, put("/api/marketing/pages/" + pageId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(version, url, departmentId, editorId))).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Final title"))
			.andExpect(jsonPath("$.status").value("ARCHIVED"))
			.andExpect(jsonPath("$.version").value(version + 1));

		String details = jdbc.queryForObject(
				"SELECT details FROM audit_logs WHERE action = 'SEO_PAGE_UPDATED' AND entity_id = ? AND actor_id = ?",
				String.class, pageId, editorId);
		assertThat(details).contains("\"title\"", "\"status\"", "\"ownerId\"").doesNotContain("\"url\"");

		// Archived pages leave the pickers and take no new keywords.
		as(editorToken, get("/api/marketing/pages/options")).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == " + pageId + ")]").isEmpty());
		as(editorToken, post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content(keyword(pageId, "anything", null))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PAGE_ARCHIVED"));

		// A page without keywords, and a keyword never ranked, can be deleted outright.
		Long openId = id(as(editorToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content(page(url + "-open", "Open", null))).andExpect(status().isCreated()));
		Long keywordId = id(as(editorToken, post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content(keyword(openId, "never ranked", null))).andExpect(status().isCreated()));
		as(editorToken, delete("/api/marketing/keywords/" + keywordId)).andExpect(status().isNoContent());
		as(editorToken, delete("/api/marketing/pages/" + openId)).andExpect(status().isNoContent());
		as(editorToken, get("/api/marketing/pages/" + openId)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SEO_PAGE_NOT_FOUND"));
		assertThat(pageRepository.existsById(openId)).isFalse();
	}

	private void record(Long keywordId, MarketingPeriod period, Integer position) {
		transactions.executeWithoutResult(status -> {
			SeoKeyword keyword = keywordRepository.findById(keywordId).orElseThrow();
			KeywordRanking ranking = new KeywordRanking();
			ranking.setKeyword(keyword);
			ranking.setPage(keyword.getPage());
			ranking.setMonth(period.month());
			ranking.setYear(period.year());
			ranking.setPosition(position);
			rankingRepository.save(ranking);
		});
	}

	private String page(String url, String title, Long ownerId) {
		return """
				{"url":"%s","title":"%s","pageType":"SERVICE","primaryKeyword":"sap testing","departmentId":%d,
				 "ownerId":%s}
				""".formatted(url, title, departmentId, ownerId);
	}

	private static String keyword(Long pageId, String text, String device) {
		return """
				{"pageId":%d,"keyword":"%s","device":%s,"targetPosition":5,"searchVolume":1900,"keywordDifficulty":48}
				""".formatted(pageId, text, device == null ? "null" : "\"" + device + "\"");
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
