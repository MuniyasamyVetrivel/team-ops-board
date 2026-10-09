package com.teamops.marketing.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

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
 * Phase 20 end-to-end against MySQL: the Digital Marketing monthly report against the month before, narrowed to what
 * each reader may see, its CSV export, and freezing an ended month (insert-only, full access only, served frozen
 * until asked for live figures). Live figures use a throwaway owner; freezing uses January 2001, which nothing else
 * touches. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class MarketingReportFlowIT {

	private static final String[] ALL_MARKETING = { "MARKETING_VIEW", "MARKETING_EDIT", "SEO_VIEW", "LEAD_VIEW", "LEAD_EDIT",
			"CAMPAIGN_VIEW", "BACKLINK_VIEW", "CONTENT_VIEW", "TARGET_VIEW" };

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

	private Long editorId;

	private String editorToken;

	private String leadsOnlyToken;

	private String partialEditorToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		editorId = users.create("DM", RoleCodes.EMPLOYEE, ALL_MARKETING);
		Long leadsOnlyId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "LEAD_VIEW");
		Long partialEditorId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "MARKETING_EDIT", "SEO_VIEW");
		editorToken = login(users.email(editorId));
		leadsOnlyToken = login(users.email(leadsOnlyId));
		partialEditorToken = login(users.email(partialEditorId));
		jdbc.update("DELETE FROM marketing_monthly_reports WHERE report_year = 2001 AND report_month = 1");
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM marketing_leads WHERE owner_id = ? OR created_by = ?", editorId, editorId);
		jdbc.update("DELETE FROM marketing_monthly_reports WHERE report_year = 2001 AND report_month = 1");
		jdbc.update("DELETE FROM audit_logs WHERE actor_id = ?", editorId);
		users.deleteAll();
	}

	@Test
	void theReportComparesWithTheMonthBeforeAndShowsEachReaderTheirPart() throws Exception {
		LocalDate today = calendar.today();
		MarketingPeriod current = MarketingPeriod.of(today);
		as(editorToken, post("/api/marketing/leads"), """
				{"name":"Anita Rao","source":"EMAIL","leadDate":"%s","ownerId":%d}
				""".formatted(today, editorId)).andExpect(status().isCreated());

		report(editorToken, "ownerId", editorId.toString()).andExpect(status().isOk())
			.andExpect(jsonPath("$.period.label").value(current.label()))
			.andExpect(jsonPath("$.comparisonPeriod.label").value(current.previous().label()))
			.andExpect(jsonPath("$.frozen").value(false))
			.andExpect(jsonPath("$.groups[*].key", Matchers.contains("SEO", "LEADS", "EMAIL", "LINKEDIN", "BACKLINKS", "CONTENT", "ACTIVITIES")))
			.andExpect(jsonPath("$.groups[1].lines[0].label").value("Leads generated"))
			.andExpect(jsonPath("$.groups[1].lines[0].current").value(1))
			.andExpect(jsonPath("$.groups[1].lines[0].previous").value(0))
			.andExpect(jsonPath("$.groups[1].lines[0].change").value(1))
			.andExpect(jsonPath("$.groups[1].lines[0].changePct").isEmpty())
			.andExpect(jsonPath("$.groups[1].lines[?(@.label == 'Email leads')].current").value(Matchers.contains(1)))
			.andExpect(jsonPath("$.keywordMovements.improved").isArray())
			.andExpect(jsonPath("$.targets").isArray());

		report(leadsOnlyToken, "ownerId", editorId.toString())
			.andExpect(jsonPath("$.groups[*].key", Matchers.contains("LEADS", "ACTIVITIES")))
			.andExpect(jsonPath("$.groups[0].lines[?(@.target == true)]").isEmpty())
			.andExpect(jsonPath("$.keywordMovements").isEmpty())
			.andExpect(jsonPath("$.targets").isEmpty());

		String csv = mvc.perform(get("/api/marketing/reports/monthly/export")
			.param("ownerId", editorId.toString())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + leadsOnlyToken))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("Digital Marketing monthly report").contains("Lead performance")
			.contains("Leads generated,1,0,1,").doesNotContain("SEO summary");
	}

	@Test
	void anEndedMonthIsFrozenOnceAndThenReportedAsItStood() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		as(editorToken, post("/api/marketing/reports/monthly/%d/%d/freeze".formatted(current.year(), current.month())), "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MONTH_NOT_ENDED"));
		as(partialEditorToken, post("/api/marketing/reports/monthly/2001/1/freeze"), "").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INCOMPLETE_ACCESS"));
		as(leadsOnlyToken, post("/api/marketing/reports/monthly/2001/1/freeze"), "").andExpect(status().isForbidden());

		as(editorToken, post("/api/marketing/reports/monthly/2001/1/freeze"), "").andExpect(status().isOk())
			.andExpect(jsonPath("$.frozen").value(true))
			.andExpect(jsonPath("$.period.label").value("January 2001"))
			.andExpect(jsonPath("$.generatedBy.id").value(editorId))
			.andExpect(jsonPath("$.groups.length()").value(7));
		as(editorToken, post("/api/marketing/reports/monthly/2001/1/freeze"), "").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REPORT_FROZEN"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'MARKETING_REPORT_FROZEN' AND actor_id = ?",
				Integer.class, editorId)).isEqualTo(1);

		// The frozen month is what the report shows, narrowed to the reader; live figures on request.
		report(leadsOnlyToken, "month", "1", "year", "2001").andExpect(jsonPath("$.frozen").value(true))
			.andExpect(jsonPath("$.groups[*].key", Matchers.contains("LEADS", "ACTIVITIES")))
			.andExpect(jsonPath("$.targets").isEmpty());
		report(editorToken, "month", "1", "year", "2001", "live", "true").andExpect(jsonPath("$.frozen").value(false));
		mvc.perform(get("/api/marketing/reports/frozen")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + leadsOnlyToken))
			.andExpect(jsonPath("$[?(@.period.label == 'January 2001')].generatedBy.id").value(Matchers.contains(editorId.intValue())));
	}

	private ResultActions report(String token, String... params) throws Exception {
		var request = get("/api/marketing/reports/monthly").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		for (int i = 0; i + 1 < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mvc.perform(request);
	}

	private ResultActions as(String token, AbstractMockHttpServletRequestBuilder<?> request, String json) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
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
