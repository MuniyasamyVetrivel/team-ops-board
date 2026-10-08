package com.teamops.marketing.paid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.mock.web.MockMultipartFile;
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
 * Phase 15 end-to-end against MySQL: paid campaign plans, monthly results (recording, corrections, the month rules),
 * rates and budget progress, the monthly summary and trend, CSV import of results and export, and the LinkedIn
 * Campaigns target type becoming automatic. Figures are filtered to a throwaway owner so seeded data never changes
 * them. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class PaidCampaignFlowIT {

	private static final String SAP_RESULTS = """
			{"amountSpent":42000,"impressions":150000,"clicks":2800,"leads":84,"conversions":21}
			""";

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

	private String viewerToken;

	private String nonce;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		editorId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "CAMPAIGN_VIEW", "CAMPAIGN_EDIT", "TARGET_VIEW");
		Long viewerId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "CAMPAIGN_VIEW");
		editorToken = login(users.email(editorId));
		viewerToken = login(users.email(viewerId));
		nonce = Long.toHexString(System.nanoTime()).toUpperCase();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE m FROM paid_campaign_metrics m JOIN paid_campaigns c ON c.id = m.campaign_id WHERE c.owner_id = ?",
				editorId);
		jdbc.update("DELETE FROM paid_campaigns WHERE owner_id = ?", editorId);
		jdbc.update("DELETE FROM audit_logs WHERE (action LIKE 'PAID_%' OR action = 'CSV_IMPORTED') AND actor_id = ?",
				editorId);
		users.deleteAll();
	}

	@Test
	void campaignsRecordMonthlyResultsWithinTheirDatesAndComputeRates() throws Exception {
		LocalDate today = calendar.today();
		MarketingPeriod current = MarketingPeriod.of(today);
		MarketingPeriod threeAgo = current.plusMonths(-3);

		Long id = id(create(plan("SAP S/4HANA Testing Campaign " + nonce, "ACTIVE", threeAgo.firstDay(), null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.campaign.platform").value("LINKEDIN"))
			.andExpect(jsonPath("$.campaign.currency").value("INR"))
			.andExpect(jsonPath("$.campaign.budgetProgress.remaining").value(50000.0))
			.andExpect(jsonPath("$.campaign.lifetime.rates.costPerLead").isEmpty())
			.andExpect(jsonPath("$.months.length()").value(0)));
		Long draftId = id(create(plan("Next quarter " + nonce, "DRAFT", current.next().firstDay(), null))
			.andExpect(status().isCreated()));
		create(plan("Backwards " + nonce, "ACTIVE", today, today.minusDays(1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATES"));

		// Results need a started campaign, a month inside its dates, not in the future, and consistent figures.
		saveMonth(draftId, current, SAP_RESULTS).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_STARTED"));
		saveMonth(id, current.next(), SAP_RESULTS).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FUTURE_PERIOD"));
		saveMonth(id, threeAgo.previous(), SAP_RESULTS).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MONTH_OUTSIDE_CAMPAIGN"));
		saveMonth(id, current, "{\"amountSpent\":10,\"impressions\":100,\"clicks\":120,\"leads\":1,\"conversions\":0}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RESULTS"))
			.andExpect(jsonPath("$.message").value("Clicks: cannot be more than the impressions"));

		// Brief section 76: ₹42,000 ÷ 84 leads = ₹500 per lead; 2,800 ÷ 150,000 = 1.87% CTR; ₹8,000 of ₹50,000 left.
		saveMonth(id, current, SAP_RESULTS).andExpect(status().isOk())
			.andExpect(jsonPath("$.months[0].figures.rates.costPerLead").value(500.0))
			.andExpect(jsonPath("$.months[0].figures.rates.ctr").value(1.87))
			.andExpect(jsonPath("$.months[0].figures.rates.conversionRate").value(25.0))
			.andExpect(jsonPath("$.months[0].source").value("MANUAL"))
			.andExpect(jsonPath("$.months[0].correctable").value(true))
			.andExpect(jsonPath("$.campaign.budgetProgress.remaining").value(8000.0))
			.andExpect(jsonPath("$.campaign.budgetProgress.usedPct").value(84.0));
		saveMonth(id, current, SAP_RESULTS).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_EXISTS"));
		saveMonth(id, current, SAP_RESULTS.replace("{", "{\"version\":5,")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		saveMonth(id, current, SAP_RESULTS.replace("{", "{\"version\":0,").replace("\"leads\":84", "\"leads\":80"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.months[0].figures.rates.costPerLead").value(525.0));
		assertThat(jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'PAID_RESULTS_CORRECTED' AND entity_id = ?",
				String.class, id)).contains("\"results\"").containsPattern("\"leads\":\\s?84");

		// A closed month can be added late, but only a Super Admin may correct it afterwards.
		saveMonth(id, threeAgo, "{\"amountSpent\":5000,\"impressions\":20000,\"clicks\":300,\"leads\":10,\"conversions\":2}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.months[0].period.month").value(threeAgo.month()))
			.andExpect(jsonPath("$.months[0].correctable").value(false))
			.andExpect(jsonPath("$.campaign.lifetime.results.leads").value(90))
			.andExpect(jsonPath("$.campaign.budgetProgress.spent").value(47000.0));
		saveMonth(id, threeAgo, "{\"version\":0,\"amountSpent\":5000,\"impressions\":20000,\"clicks\":300,\"leads\":11,\"conversions\":2}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));

		// The month across this owner's campaigns, against the previous month (nothing recorded: rates are null).
		as(viewerToken, get("/api/marketing/paid-campaigns/summary").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.period.month").value(current.month()))
			.andExpect(jsonPath("$.current.campaigns").value(1))
			.andExpect(jsonPath("$.current.figures.results.spend").value(42000.0))
			.andExpect(jsonPath("$.current.figures.rates.costPerLead").value(525.0))
			.andExpect(jsonPath("$.comparison.campaigns").value(0))
			.andExpect(jsonPath("$.comparison.figures.rates.costPerLead").isEmpty())
			.andExpect(jsonPath("$.byPlatform[0].platform").value("LINKEDIN"))
			// The running (non-draft) campaign's budget against its spend to date: ₹47,000 of ₹50,000.
			.andExpect(jsonPath("$.budget.campaigns").value(1))
			.andExpect(jsonPath("$.budget.progress.budget").value(50000.0))
			.andExpect(jsonPath("$.budget.progress.spent").value(47000.0))
			.andExpect(jsonPath("$.budget.progress.remaining").value(3000.0))
			.andExpect(jsonPath("$.budget.progress.usedPct").value(94.0));
		as(viewerToken, get("/api/marketing/paid-campaigns/trend").param("ownerId", editorId.toString()).param("months", "4"))
			.andExpect(jsonPath("$.months.length()").value(4))
			.andExpect(jsonPath("$.months[0].figures.results.leads").value(10))
			.andExpect(jsonPath("$.months[1].campaigns").value(0))
			.andExpect(jsonPath("$.months[3].figures.results.leads").value(80));
		as(viewerToken, get("/api/marketing/paid-campaigns").param("ownerId", editorId.toString())
			.param("month", String.valueOf(current.month()))
			.param("year", String.valueOf(current.year()))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(id))
			.andExpect(jsonPath("$.content[0].month.results.spend").value(42000.0))
			.andExpect(jsonPath("$.content[0].lifetime.results.spend").value(47000.0));

		// Recorded months are history: they pin the dates and status, and stop deletion.
		update(id, plan("SAP S/4HANA Testing Campaign " + nonce, "DRAFT", threeAgo.firstDay(), null), 0)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CAMPAIGN_HAS_RESULTS"));
		update(id, plan("SAP S/4HANA Testing Campaign " + nonce, "ACTIVE", threeAgo.next().firstDay(), null), 0)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESULTS_OUTSIDE_DATES"));
		update(id, plan("SAP S/4HANA Testing Campaign " + nonce, "PAUSED", threeAgo.firstDay(), null), 0)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.campaign.status").value("PAUSED"));
		assertThat(jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'PAID_CAMPAIGN_UPDATED' AND entity_id = ?",
				String.class, id)).contains("\"status\"").contains("PAUSED");
		as(editorToken, delete("/api/marketing/paid-campaigns/" + id)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CAMPAIGN_HAS_RESULTS"));
		as(viewerToken, delete("/api/marketing/paid-campaigns/" + draftId)).andExpect(status().isForbidden());
		as(editorToken, delete("/api/marketing/paid-campaigns/" + draftId)).andExpect(status().isNoContent());

		// Campaigns now feed the LinkedIn Campaigns target type.
		as(editorToken, get("/api/marketing/target-types"))
			.andExpect(jsonPath("$[?(@.code == 'LINKEDIN_CAMPAIGNS')].automatic").value(Matchers.contains(true)));
	}

	@Test
	void csvImportAddsMonthsOnceAndExportListsCampaigns() throws Exception {
		LocalDate today = calendar.today();
		MarketingPeriod current = MarketingPeriod.of(today);
		MarketingPeriod previous = current.previous();
		String name = "Webinar promotion " + nonce;
		create(plan(name, "ACTIVE", previous.firstDay(), null)).andExpect(status().isCreated());

		as(editorToken, multipart("/api/marketing/imports/paid-campaign-results/preview").file(csv("""
				campaign,month,year,amount_spent,impressions,clicks,leads,conversions
				%1$s,%2$d,%3$d,"27,500",96000,1350,41,12
				%1$s,%4$d,%5$d,100,100,150,1,0
				Unknown %6$s,%4$d,%5$d,100,1000,10,1,0
				%1$s,%7$d,%8$d,100,1000,10,1,0
				""".formatted(name, previous.month(), previous.year(), current.month(), current.year(), nonce,
				current.next().month(), current.next().year())))).andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(3))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].column").value("clicks"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].column").value("campaign"))
			.andExpect(jsonPath("$.invalidRows[2].errors[0].column").value("month"));

		MockMultipartFile file = csv("""
				campaign,month,year,amount_spent,impressions,clicks,leads
				%s,%d,%d,27500,96000,1350,41
				""".formatted(name, previous.month(), previous.year()));
		String preview = as(editorToken, multipart("/api/marketing/imports/paid-campaign-results/preview").file(file))
			.andReturn().getResponse().getContentAsString();
		as(editorToken, multipart("/api/marketing/imports/paid-campaign-results/commit").file(file)
			.param("checksum", (String) JsonPath.read(preview, "$.checksum"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(1));
		assertThat(jdbc.queryForMap("SELECT m.source, m.conversions, m.amount_spent FROM paid_campaign_metrics m "
				+ "JOIN paid_campaigns c ON c.id = m.campaign_id WHERE c.name = ?", name)).containsEntry("source", "CSV")
			.containsEntry("conversions", 0);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'PAID_RESULTS_RECORDED' AND actor_id = ?",
				Integer.class, editorId)).isEqualTo(1);

		// Months only get added: the same month again is rejected.
		as(editorToken, multipart("/api/marketing/imports/paid-campaign-results/preview").file(file))
			.andExpect(jsonPath("$.validCount").value(0))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].column").value("month"));

		String csv = as(viewerToken, get("/api/marketing/paid-campaigns/export").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains(name + ",LINKEDIN,LEAD_GENERATION,ACTIVE").contains(",27500.00,96000,1350,41,0,1.41,670.73,");
	}

	private ResultActions create(String json) throws Exception {
		return as(editorToken, post("/api/marketing/paid-campaigns").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions update(Long id, String json, int version) throws Exception {
		return as(editorToken, put("/api/marketing/paid-campaigns/" + id).contentType(MediaType.APPLICATION_JSON)
			.content(json.replace("{", "{\"version\":" + version + ",")));
	}

	private ResultActions saveMonth(Long id, MarketingPeriod period, String json) throws Exception {
		return as(editorToken, put("/api/marketing/paid-campaigns/%d/results/%d/%d".formatted(id, period.year(), period.month()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private String plan(String name, String status, LocalDate start, LocalDate end) {
		return """
				{"name":"%s","objective":"LEAD_GENERATION","startDate":"%s","endDate":%s,"budget":50000,
				 "status":"%s","ownerId":%d}
				""".formatted(name, start, end == null ? "null" : "\"" + end + "\"", status, editorId);
	}

	private static MockMultipartFile csv(String text) {
		return new MockMultipartFile("file", "results.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
	}

	private static Long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.campaign.id")).longValue();
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
