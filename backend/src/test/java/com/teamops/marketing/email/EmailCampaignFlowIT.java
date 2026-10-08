package com.teamops.marketing.email;

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
 * Phase 14 end-to-end against MySQL: email campaign CRUD with count checks, rates, the monthly summary and its
 * comparison, the trend, CSV import (with duplicate protection) and export, and the Email Campaigns target type
 * becoming automatic. Figures are filtered to a throwaway owner so seeded data never changes them. Run with: mvnw
 * verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class EmailCampaignFlowIT {

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
		jdbc.update("DELETE FROM email_campaigns WHERE owner_id = ? OR external_id LIKE ?", editorId, "IT-" + nonce + "%");
		jdbc.update("DELETE FROM audit_logs WHERE (action LIKE 'EMAIL_CAMPAIGN_%' OR action = 'CSV_IMPORTED') AND actor_id = ?",
				editorId);
		users.deleteAll();
	}

	@Test
	void campaignsCarryCountsOnlyOnceSentAndComputeTheirRates() throws Exception {
		LocalDate today = calendar.today();
		MarketingPeriod current = MarketingPeriod.of(today);

		// Counts belong to sent campaigns, must be consistent, and a sent campaign is not dated in the future.
		create(campaign("Draft with counts", "DRAFT", today.plusDays(3), 100, 90, 10)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COUNTS_NEED_SENT"));
		create(campaign("Impossible", "SENT", today, 100, 120, 10)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_COUNTS"))
			.andExpect(jsonPath("$.message").value("Delivered: cannot be more than the emails sent"));
		create(campaign("Tomorrow", "SENT", today.plusDays(1), 100, 90, 10)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FUTURE_SEND_DATE"));

		// Brief section 77: open rate 8,500 ÷ 24,000 = 35.42%.
		String sent = create(campaign("SAP Testing Services Outreach", "SENT", today, 25_000, 24_000, 8_500))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.rates.openRate").value(35.42))
			.andExpect(jsonPath("$.rates.clickRate").value(5.21))
			.andExpect(jsonPath("$.rates.leadConversionRate").value(0.77))
			.andExpect(jsonPath("$.rates.deliveryRate").value(96.0))
			.andExpect(jsonPath("$.provider").value("MANUAL"))
			.andReturn().getResponse().getContentAsString();
		Long sentId = ((Number) JsonPath.read(sent, "$.id")).longValue();
		Long draftId = id(create(campaign("Next newsletter", "DRAFT", today.plusDays(10), 0, 0, 0)).andExpect(status().isCreated()));

		// The month's summary for this owner, against a month without campaigns.
		as(viewerToken, get("/api/marketing/email-campaigns/summary").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.period.month").value(current.month()))
			.andExpect(jsonPath("$.current.campaigns").value(1))
			.andExpect(jsonPath("$.current.counts.emailsSent").value(25_000))
			.andExpect(jsonPath("$.current.counts.leads").value(185))
			.andExpect(jsonPath("$.current.rates.openRate").value(35.42))
			.andExpect(jsonPath("$.comparison.period.month").value(current.previous().month()))
			.andExpect(jsonPath("$.comparison.campaigns").value(0))
			.andExpect(jsonPath("$.comparison.rates.openRate").isEmpty())
			.andExpect(jsonPath("$.byType[0].campaignType").value("LEAD_GENERATION"));
		as(viewerToken, get("/api/marketing/email-campaigns/trend").param("ownerId", editorId.toString()).param("months", "3"))
			.andExpect(jsonPath("$.months.length()").value(3))
			.andExpect(jsonPath("$.months[2].counts.uniqueOpens").value(8_500))
			.andExpect(jsonPath("$.months[0].campaigns").value(0));
		as(viewerToken, get("/api/marketing/email-campaigns").param("ownerId", editorId.toString())
			.param("month", String.valueOf(current.month()))
			.param("year", String.valueOf(current.year()))
			.param("status", "SENT")).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(sentId));

		// Corrections are versioned and audited; a sent campaign is history.
		as(editorToken, put("/api/marketing/email-campaigns/" + sentId).contentType(MediaType.APPLICATION_JSON)
			.content(campaign("SAP Testing Services Outreach", "SENT", today, 25_000, 24_000, 8_600).replace("{", "{\"version\":7,")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(editorToken, put("/api/marketing/email-campaigns/" + sentId).contentType(MediaType.APPLICATION_JSON)
			.content(campaign("SAP Testing Services Outreach", "SENT", today, 25_000, 24_000, 8_600).replace("{", "{\"version\":0,")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.uniqueOpens").value(8_600))
			.andExpect(jsonPath("$.rates.openRate").value(35.83));
		assertThat(jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'EMAIL_CAMPAIGN_UPDATED' AND entity_id = ?",
				String.class, sentId)).contains("\"counts\"").containsPattern("\"uniqueOpens\":\\s?8500");
		as(editorToken, delete("/api/marketing/email-campaigns/" + sentId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CAMPAIGN_SENT"));
		as(viewerToken, delete("/api/marketing/email-campaigns/" + draftId)).andExpect(status().isForbidden());
		as(editorToken, delete("/api/marketing/email-campaigns/" + draftId)).andExpect(status().isNoContent());

		// Campaigns now feed the Email Campaigns target type.
		as(editorToken, get("/api/marketing/target-types"))
			.andExpect(jsonPath("$[?(@.code == 'EMAIL_CAMPAIGNS')].automatic").value(Matchers.contains(true)));
	}

	@Test
	void csvImportAddsSentCampaignsOnceAndExportListsThem() throws Exception {
		LocalDate today = calendar.today();
		String email = users.email(editorId);
		as(editorToken, multipart("/api/marketing/imports/email-campaigns/preview").file(csv("""
				name,campaign_type,campaign_date,emails_sent,delivered,unique_opens,unique_clicks,leads,owner_email,external_id
				Webinar invite,EVENT,%1$s,6000,5820,2050,360,38,%2$s,IT-%3$s-1
				Bad counts,NEWSLETTER,%1$s,100,150,10,5,0,%2$s,IT-%3$s-2
				Future send,NEWSLETTER,%4$s,100,90,10,5,0,%2$s,IT-%3$s-3
				""".formatted(today, email, nonce, today.plusDays(2))))).andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(2))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].column").value("delivered"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].column").value("campaign_date"));

		MockMultipartFile file = csv("""
				name,campaign_type,campaign_date,emails_sent,delivered,unique_opens,unique_clicks,leads,owner_email,external_id
				Webinar invite,EVENT,%s,6000,5820,2050,360,38,%s,IT-%s-1
				""".formatted(today, email, nonce));
		String preview = as(editorToken, multipart("/api/marketing/imports/email-campaigns/preview").file(file))
			.andReturn().getResponse().getContentAsString();
		as(editorToken, multipart("/api/marketing/imports/email-campaigns/commit").file(file)
			.param("checksum", (String) JsonPath.read(preview, "$.checksum"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(1));
		// Defaults filled in: bounces = sent − delivered, total opens and clicks = the unique ones.
		assertThat(jdbc.queryForMap("SELECT status, provider, bounced, opened, clicked FROM email_campaigns WHERE external_id = ?",
				"IT-" + nonce + "-1")).containsEntry("status", "SENT")
			.containsEntry("provider", "CSV")
			.containsEntry("bounced", 180)
			.containsEntry("opened", 2050)
			.containsEntry("clicked", 360);

		// The same campaign cannot be imported twice.
		as(editorToken, multipart("/api/marketing/imports/email-campaigns/preview").file(file))
			.andExpect(jsonPath("$.validCount").value(0))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message").value("This campaign was already imported"));

		String csv = as(viewerToken, get("/api/marketing/email-campaigns/export").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("Webinar invite,EVENT," + today + ",SENT").contains(",35.22,");
	}

	private ResultActions create(String json) throws Exception {
		return as(editorToken, post("/api/marketing/email-campaigns").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String campaign(String name, String status, LocalDate date, int sent, int delivered, int uniqueOpens) {
		// Opens, clicks and leads beyond the given ones only for a large send, so small rows stay consistent.
		boolean counted = sent >= 1_000;
		return """
				{"name":"%s","campaignType":"LEAD_GENERATION","campaignDate":"%s","status":"%s","ownerId":%d,
				 "audience":"SAP decision makers","emailsSent":%d,"delivered":%d,"bounced":%d,"opened":%d,
				 "uniqueOpens":%d,"clicked":%d,"uniqueClicks":%d,"unsubscribed":%d,"leadsGenerated":%d}
				""".formatted(name, date, status, editorId, sent, delivered, Math.max(sent - delivered, 0),
				uniqueOpens + (counted ? 900 : 0), uniqueOpens, counted ? 1_400 : 0, counted ? 1_250 : 0,
				counted ? 60 : 0, counted ? 185 : 0);
	}

	private static MockMultipartFile csv(String text) {
		return new MockMultipartFile("file", "campaigns.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
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
