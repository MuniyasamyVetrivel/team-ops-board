package com.teamops.marketing.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.marketing.target.repository.TargetTypeRepository;
import com.teamops.marketing.target.service.TargetActuals;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 16 end-to-end against MySQL: leads with their link rules (source, one link, live, not before the campaign),
 * the month lock (and the Super Admin exception), versioned and audited changes, the summary by source and status,
 * the Website and per-source lead targets computing their actuals, campaign deletion guards, CSV import (with
 * duplicate protection) and export. Leads are owned by a throwaway user and target actuals are compared before and
 * after, so seeded data never changes the checks. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class LeadFlowIT {

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

	@Autowired
	private TargetActuals targetActuals;

	@Autowired
	private TargetTypeRepository typeRepository;

	private IntegrationUsers users;

	private Long editorId;

	private Long adminId;

	private String editorToken;

	private String viewerToken;

	private String adminToken;

	private String nonce;

	private LocalDate today;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		editorId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "LEAD_VIEW", "LEAD_EDIT", "TARGET_VIEW",
				"CAMPAIGN_VIEW", "CAMPAIGN_EDIT");
		Long viewerId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "LEAD_VIEW");
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		editorToken = login(users.email(editorId));
		viewerToken = login(users.email(viewerId));
		adminToken = login(users.email(adminId));
		nonce = "IT-" + Long.toHexString(System.nanoTime()).toUpperCase();
		today = calendar.today();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM marketing_leads WHERE owner_id = ? OR created_by IN (?, ?)", editorId, editorId, adminId);
		jdbc.update("DELETE FROM paid_campaigns WHERE owner_id = ?", editorId);
		jdbc.update("DELETE FROM email_campaigns WHERE owner_id = ?", editorId);
		jdbc.update("DELETE FROM content_items WHERE title LIKE ?", nonce + "%");
		jdbc.update("""
				DELETE FROM audit_logs WHERE actor_id IN (?, ?) AND (action LIKE 'LEAD_%' OR action LIKE 'EMAIL_CAMPAIGN_%'
				  OR action LIKE 'PAID_CAMPAIGN_%' OR action = 'CSV_IMPORTED')
				""", editorId, adminId);
		users.deleteAll();
	}

	@Test
	void leadsFollowTheLinkAndMonthRulesAndFeedTheLeadTargets() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(today);
		BigDecimal websiteBefore = actual("WEBSITE_LEADS", current);
		BigDecimal emailBefore = actual("EMAIL_LEADS", current);
		Long emailCampaignId = emailCampaign(nonce + " outreach");
		Long paidCampaignId = id(as(editorToken, post("/api/marketing/paid-campaigns").contentType(MediaType.APPLICATION_JSON)
			.content(paidPlan("ACTIVE"))).andExpect(status().isCreated()), "$.campaign.id");
		Long contentId = content(nonce + " SAP testing post", today);

		// What a lead may not be.
		create(lead("Tomorrow", "ORGANIC", today.plusDays(1), null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FUTURE_LEAD_DATE"));
		LocalDate closed = current.plusMonths(-3).firstDay().plusDays(9);
		create(lead("Old lead", "ORGANIC", closed, null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));
		create(lead("Wrong link", "EMAIL", today, "\"paidCampaignId\":" + paidCampaignId)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("LINK_NOT_ALLOWED"))
			.andExpect(jsonPath("$.message").value("Only LinkedIn and paid campaign leads can name a paid campaign"));
		create(lead("Two links", "EMAIL", today, "\"emailCampaignId\":" + emailCampaignId + ",\"contentItemId\":" + contentId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ONE_LINK"));
		create(lead("Too early", "EMAIL", today.minusDays(1), "\"emailCampaignId\":" + emailCampaignId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("LEAD_BEFORE_LINK"));

		// Four leads from four sources, three of them naming what brought them in.
		Long emailLead = id(create(lead("Anita Rao", "EMAIL", today, "\"emailCampaignId\":" + emailCampaignId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value(Matchers.startsWith("LEAD-")))
			.andExpect(jsonPath("$.link.kind").value("EMAIL_CAMPAIGN"))
			.andExpect(jsonPath("$.link.id").value(emailCampaignId))
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.countLocked").value(false)), "$.id");
		Long linkedinLead = id(create(lead("Vikram Iyer", "LINKEDIN", today, "\"paidCampaignId\":" + paidCampaignId))
			.andExpect(status().isCreated()), "$.id");
		create(lead("Meera Menon", "BLOG", today, "\"contentItemId\":" + contentId)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.link.name").value(nonce + " SAP testing post"));
		Long organicLead = id(create(lead("Rahul Sharma", "ORGANIC", today, null)).andExpect(status().isCreated()), "$.id");

		// Brief section 44: the month by source, by status, and what brought leads in.
		as(editorToken, get("/api/marketing/leads/summary").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(4))
			.andExpect(jsonPath("$.bySource.length()").value(8))
			.andExpect(jsonPath("$.bySource[?(@.source == 'EMAIL')].leads").value(Matchers.contains(1)))
			.andExpect(jsonPath("$.bySource[?(@.source == 'WEBSITE')].leads").value(Matchers.contains(0)))
			.andExpect(jsonPath("$.byStatus[?(@.status == 'NEW')].leads").value(Matchers.contains(4)))
			.andExpect(jsonPath("$.convertedPct").value(0.0))
			.andExpect(jsonPath("$.topLinks.length()").value(3))
			.andExpect(jsonPath("$.targetsVisible").value(true));
		as(viewerToken, get("/api/marketing/leads/summary").param("ownerId", editorId.toString()))
			.andExpect(jsonPath("$.targetsVisible").value(false))
			.andExpect(jsonPath("$.totalTarget").isEmpty());
		as(viewerToken, get("/api/marketing/leads/trend").param("ownerId", editorId.toString()).param("months", "3"))
			.andExpect(jsonPath("$.months.length()").value(3))
			.andExpect(jsonPath("$.months[2].total").value(4))
			.andExpect(jsonPath("$.months[2].bySource.LINKEDIN").value(1))
			.andExpect(jsonPath("$.months[0].total").value(0));

		// The Website Leads and Email Leads targets now count leads.
		assertThat(actual("WEBSITE_LEADS", current)).isEqualByComparingTo(websiteBefore.add(BigDecimal.valueOf(4)));
		assertThat(actual("EMAIL_LEADS", current)).isEqualByComparingTo(emailBefore.add(BigDecimal.ONE));
		as(editorToken, get("/api/marketing/target-types"))
			.andExpect(jsonPath("$[?(@.code == 'WEBSITE_LEADS')].automatic").value(Matchers.contains(true)))
			.andExpect(jsonPath("$[?(@.code == 'LINKEDIN_LEADS')].automatic").value(Matchers.contains(true)));

		// Filters and the link picker.
		as(viewerToken, get("/api/marketing/leads").param("paidCampaignId", paidCampaignId.toString()))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(linkedinLead));
		as(viewerToken, get("/api/marketing/leads/link-options").param("kind", "EMAIL_CAMPAIGN").param("search", nonce))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(emailCampaignId));

		// Campaigns that leads name are kept.
		as(editorToken, put("/api/marketing/email-campaigns/" + emailCampaignId).contentType(MediaType.APPLICATION_JSON)
			.content(emailCampaignJson(nonce + " outreach", "DRAFT").replace("{", "{\"version\":0,")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CAMPAIGN_HAS_LEADS"));
		as(editorToken, put("/api/marketing/paid-campaigns/" + paidCampaignId).contentType(MediaType.APPLICATION_JSON)
			.content(paidPlan("DRAFT").replace("{", "{\"version\":0,")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CAMPAIGN_HAS_LEADS"));
		as(editorToken, delete("/api/marketing/paid-campaigns/" + paidCampaignId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CAMPAIGN_HAS_LEADS"));

		// Changes are versioned, checked and audited.
		as(editorToken, put("/api/marketing/leads/" + emailLead).contentType(MediaType.APPLICATION_JSON)
			.content(lead("Anita Rao", "ORGANIC", today, "\"emailCampaignId\":" + emailCampaignId).replace("{", "{\"version\":4,")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(editorToken, put("/api/marketing/leads/" + emailLead).contentType(MediaType.APPLICATION_JSON)
			.content(lead("Anita Rao", "ORGANIC", today, "\"emailCampaignId\":" + emailCampaignId).replace("{", "{\"version\":0,")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("LINK_NOT_ALLOWED"));
		as(editorToken, put("/api/marketing/leads/" + emailLead).contentType(MediaType.APPLICATION_JSON)
			.content(lead("Anita Rao", "ORGANIC", today, null).replace("{", "{\"version\":0,")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("ORGANIC"))
			.andExpect(jsonPath("$.link").isEmpty());
		assertThat(jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'LEAD_UPDATED' AND entity_id = ?",
				String.class, emailLead)).contains("\"source\"").contains("EMAIL_CAMPAIGN:" + emailCampaignId);
		as(editorToken, put("/api/marketing/leads/" + emailLead + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":1,\"status\":\"CONVERTED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONVERTED"));
		assertThat(jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'LEAD_STATUS_CHANGED' AND entity_id = ?",
				String.class, emailLead)).contains("CONVERTED");
		as(editorToken, get("/api/marketing/leads/summary").param("ownerId", editorId.toString()))
			.andExpect(jsonPath("$.convertedPct").value(25.0))
			.andExpect(jsonPath("$.topLinks.length()").value(2));
		assertThat(actual("EMAIL_LEADS", current)).isEqualByComparingTo(emailBefore);

		as(viewerToken, delete("/api/marketing/leads/" + organicLead)).andExpect(status().isForbidden());
		as(editorToken, delete("/api/marketing/leads/" + organicLead)).andExpect(status().isNoContent());
		assertThat(actual("WEBSITE_LEADS", current)).isEqualByComparingTo(websiteBefore.add(BigDecimal.valueOf(3)));

		// A closed month: only a Super Admin adds or removes leads there; anyone may still update its status.
		Long late = id(as(adminToken, post("/api/marketing/leads").contentType(MediaType.APPLICATION_JSON)
			.content(lead("Late entry", "ORGANIC", closed, null))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.countLocked").value(false)), "$.id");
		as(editorToken, get("/api/marketing/leads/" + late)).andExpect(jsonPath("$.countLocked").value(true));
		as(editorToken, delete("/api/marketing/leads/" + late)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));
		as(editorToken, put("/api/marketing/leads/" + late + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"QUALIFIED\"}")).andExpect(status().isOk());
		as(adminToken, delete("/api/marketing/leads/" + late)).andExpect(status().isNoContent());
	}

	@Test
	void csvImportAddsLeadsOnceAndExportListsThem() throws Exception {
		String campaign = nonce + " webinar";
		Long emailCampaignId = emailCampaign(campaign);
		String owner = users.email(editorId);
		String header = "name,source,lead_date,company,email,campaign,owner_email,external_id\n";

		as(editorToken, multipart("/api/marketing/imports/marketing-leads/preview").file(csv(header + """
				Anita Rao,EMAIL,%1$s,Acme,anita@%2$s.example,%3$s,%4$s,%2$s-1
				Future Lead,ORGANIC,%5$s,Acme,future@%2$s.example,,%4$s,%2$s-2
				Organic With Campaign,ORGANIC,%1$s,Acme,organic@%2$s.example,%3$s,%4$s,%2$s-3
				Unknown Campaign,EMAIL,%1$s,Acme,unknown@%2$s.example,No such campaign %2$s,%4$s,%2$s-4
				""".formatted(today, nonce, campaign, owner, today.plusDays(1))))).andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(3))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].column").value("lead_date"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].column").value("campaign"))
			.andExpect(jsonPath("$.invalidRows[2].errors[0].column").value("campaign"));

		MockMultipartFile file = csv(header + "Anita Rao,EMAIL,%s,Acme,anita@%s.example,%s,%s,%s-1\n"
			.formatted(today, nonce, campaign, owner, nonce));
		String preview = as(editorToken, multipart("/api/marketing/imports/marketing-leads/preview").file(file))
			.andReturn().getResponse().getContentAsString();
		as(editorToken, multipart("/api/marketing/imports/marketing-leads/commit").file(file)
			.param("checksum", (String) JsonPath.read(preview, "$.checksum"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(1));
		assertThat(jdbc.queryForMap("SELECT provider, email_campaign_id, status FROM marketing_leads WHERE external_id = ?",
				nonce + "-1")).containsEntry("provider", "CSV")
			.containsEntry("email_campaign_id", emailCampaignId)
			.containsEntry("status", "NEW");

		// Already imported (by external id), and the same email on the same date without one.
		as(editorToken, multipart("/api/marketing/imports/marketing-leads/preview").file(csv(header + """
				Anita Rao,EMAIL,%1$s,Acme,anita@%2$s.example,%3$s,%4$s,%2$s-1
				Anita Rao,ORGANIC,%1$s,Acme,anita@%2$s.example,,%4$s,
				""".formatted(today, nonce, campaign, owner)))).andExpect(jsonPath("$.validCount").value(0))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message").value("This lead was already imported"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].column").value("email"));

		String csv = as(viewerToken, get("/api/marketing/leads/export").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("Anita Rao,Acme,anita@" + nonce.toLowerCase() + ".example")
			.contains(",EMAIL," + campaign + "," + today + ",NEW,");
	}

	private BigDecimal actual(String typeCode, MarketingPeriod period) {
		TargetType type = typeRepository.findAllByOrderByPositionAscNameAsc()
			.stream()
			.filter(t -> t.getCode().equals(typeCode))
			.findFirst()
			.orElseThrow();
		return targetActuals.computed(type, period, period).get(period);
	}

	private Long emailCampaign(String name) throws Exception {
		return id(as(editorToken, post("/api/marketing/email-campaigns").contentType(MediaType.APPLICATION_JSON)
			.content(emailCampaignJson(name, "SENT"))).andExpect(status().isCreated()), "$.id");
	}

	private String emailCampaignJson(String name, String status) {
		boolean sent = status.equals("SENT");
		return """
				{"name":"%s","campaignType":"LEAD_GENERATION","campaignDate":"%s","status":"%s","ownerId":%d,
				 "emailsSent":%d,"delivered":%d,"bounced":%d,"opened":%d,"uniqueOpens":%d,"clicked":%d,
				 "uniqueClicks":%d,"unsubscribed":0,"leadsGenerated":%d}
				""".formatted(name, today, status, editorId, sent ? 100 : 0, sent ? 90 : 0, sent ? 10 : 0, sent ? 20 : 0,
				sent ? 10 : 0, sent ? 5 : 0, sent ? 3 : 0, sent ? 2 : 0);
	}

	private String paidPlan(String status) {
		return """
				{"name":"%s LinkedIn","platform":"LINKEDIN","objective":"LEAD_GENERATION","startDate":"%s","budget":50000,
				 "status":"%s","ownerId":%d}
				""".formatted(nonce, MarketingPeriod.of(today).firstDay(), status, editorId);
	}

	private Long content(String title, LocalDate published) {
		jdbc.update("INSERT INTO content_items (title, content_type, status, publication_date) VALUES (?, 'BLOG', 'PUBLISHED', ?)",
				title, published);
		return jdbc.queryForObject("SELECT id FROM content_items WHERE title = ?", Long.class, title);
	}

	private String lead(String name, String source, LocalDate date, String link) {
		return """
				{"name":"%s","company":"Acme Manufacturing","source":"%s","leadDate":"%s","ownerId":%d%s}
				""".formatted(name, source, date, editorId, link == null ? "" : "," + link);
	}

	private ResultActions create(String json) throws Exception {
		return as(editorToken, post("/api/marketing/leads").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static MockMultipartFile csv(String text) {
		return new MockMultipartFile("file", "leads.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
	}

	private static Long id(ResultActions result, String path) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
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
