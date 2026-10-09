package com.teamops.marketing.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Phase 19 end-to-end against MySQL: the executive dashboard aggregates every module for a throwaway owner (a lead, a
 * backlink, a blog post, an email campaign and a LinkedIn month recorded through the API), returns the trend for the
 * requested length, hides each section without its view permission and refuses viewers without MARKETING_VIEW. The
 * owner filter keeps seeded data out of the checks. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class MarketingDashboardFlowIT {

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

	private Long ownerId;

	private String ownerToken;

	private String marketingOnlyToken;

	private String outsiderToken;

	private String nonce;

	private LocalDate today;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		ownerId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "SEO_VIEW", "LEAD_VIEW", "LEAD_EDIT", "CAMPAIGN_VIEW",
				"CAMPAIGN_EDIT", "BACKLINK_VIEW", "BACKLINK_EDIT", "CONTENT_VIEW", "CONTENT_EDIT", "TARGET_VIEW");
		Long marketingOnlyId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW");
		Long outsiderId = users.create("IT", RoleCodes.EMPLOYEE);
		ownerToken = login(users.email(ownerId));
		marketingOnlyToken = login(users.email(marketingOnlyId));
		outsiderToken = login(users.email(outsiderId));
		nonce = "it" + Long.toHexString(System.nanoTime());
		today = calendar.today();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM marketing_leads WHERE owner_id = ? OR created_by = ?", ownerId, ownerId);
		jdbc.update("DELETE FROM backlinks WHERE owner_id = ? OR created_by = ?", ownerId, ownerId);
		jdbc.update("DELETE FROM content_items WHERE owner_id = ? OR created_by = ?", ownerId, ownerId);
		jdbc.update("DELETE FROM paid_campaign_metrics WHERE campaign_id IN (SELECT id FROM paid_campaigns WHERE owner_id = ?)", ownerId);
		jdbc.update("DELETE FROM paid_campaigns WHERE owner_id = ?", ownerId);
		jdbc.update("DELETE FROM email_campaigns WHERE owner_id = ?", ownerId);
		jdbc.update("DELETE FROM audit_logs WHERE actor_id = ?", ownerId);
		users.deleteAll();
	}

	@Test
	void theDashboardAggregatesEveryModuleForTheOwner() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(today);
		as(post("/api/marketing/leads"), """
				{"name":"Anita Rao","source":"ORGANIC","leadDate":"%s","ownerId":%d}
				""".formatted(today, ownerId)).andExpect(status().isCreated());
		as(post("/api/marketing/backlinks"), """
				{"targetUrl":"/it/%s","linkUrl":"https://blog-%s.com/a","linkType":"GUEST_POST","status":"SUBMITTED",
				 "submittedDate":"%s","ownerId":%d}
				""".formatted(nonce, nonce, today, ownerId)).andExpect(status().isCreated());
		as(post("/api/marketing/content"), """
				{"title":"%s post","contentType":"BLOG","status":"PUBLISHED","url":"/blog/%s","publicationDate":"%s","ownerId":%d}
				""".formatted(nonce, nonce, today, ownerId)).andExpect(status().isCreated());
		as(post("/api/marketing/email-campaigns"), """
				{"name":"%s newsletter","campaignType":"NEWSLETTER","campaignDate":"%s","status":"SENT","ownerId":%d,
				 "emailsSent":1000,"delivered":900,"bounced":100,"opened":400,"uniqueOpens":300,"clicked":60,
				 "uniqueClicks":45,"unsubscribed":2,"leadsGenerated":9}
				""".formatted(nonce, today, ownerId)).andExpect(status().isCreated());
		String paid = as(post("/api/marketing/paid-campaigns"), """
				{"name":"%s LinkedIn","platform":"LINKEDIN","objective":"LEAD_GENERATION","startDate":"%s","budget":50000,
				 "status":"ACTIVE","ownerId":%d}
				""".formatted(nonce, current.firstDay(), ownerId)).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long paidId = ((Number) JsonPath.read(paid, "$.campaign.id")).longValue();
		as(put("/api/marketing/paid-campaigns/%d/results/%d/%d".formatted(paidId, current.year(), current.month())),
				"{\"amountSpent\":42000,\"impressions\":150000,\"clicks\":2800,\"leads\":84,\"conversions\":21}")
			.andExpect(status().isOk());

		mvc.perform(get("/api/marketing/dashboard").param("ownerId", ownerId.toString())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.period.label").value(current.label()))
			.andExpect(jsonPath("$.comparisonPeriod.label").value(current.previous().label()))
			.andExpect(jsonPath("$.targetsVisible").value(true))
			.andExpect(jsonPath("$.seo.totalPages").value(0))
			.andExpect(jsonPath("$.leads.total").value(1))
			.andExpect(jsonPath("$.leads.bySource[?(@.source == 'ORGANIC')].leads").value(Matchers.contains(1)))
			.andExpect(jsonPath("$.email.current.campaigns").value(1))
			.andExpect(jsonPath("$.email.current.rates.openRate").value(33.33))
			.andExpect(jsonPath("$.linkedin.current.results.leads").value(84))
			.andExpect(jsonPath("$.linkedin.current.rates.costPerLead").value(500.0))
			.andExpect(jsonPath("$.linkedin.runningCampaigns").value(1))
			.andExpect(jsonPath("$.backlinks.current.submitted").value(1))
			.andExpect(jsonPath("$.content.current.publishedBlogs").value(1))
			.andExpect(jsonPath("$.targets.targets").isArray())
			.andExpect(jsonPath("$.activities.due").value(0))
			.andExpect(jsonPath("$.activities.completionPct").isEmpty())
			.andExpect(jsonPath("$.trend.length()").value(6))
			.andExpect(jsonPath("$.trend[5].leads").value(1))
			.andExpect(jsonPath("$.trend[5].linkedinLeads").value(84))
			.andExpect(jsonPath("$.trend[5].backlinksLive").value(0))
			.andExpect(jsonPath("$.trend[5].blogsPublished").value(1))
			.andExpect(jsonPath("$.trend[0].leads").value(0));

		// The trend length is the viewer's choice (2 to 24 months).
		mvc.perform(get("/api/marketing/dashboard").param("ownerId", ownerId.toString())
			.param("months", "12")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)).andExpect(jsonPath("$.trend.length()").value(12));
	}

	@Test
	void sectionsFollowTheViewersPermissions() throws Exception {
		mvc.perform(get("/api/marketing/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + marketingOnlyToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.seo").isEmpty())
			.andExpect(jsonPath("$.leads").isEmpty())
			.andExpect(jsonPath("$.email").isEmpty())
			.andExpect(jsonPath("$.linkedin").isEmpty())
			.andExpect(jsonPath("$.backlinks").isEmpty())
			.andExpect(jsonPath("$.content").isEmpty())
			.andExpect(jsonPath("$.targets").isEmpty())
			.andExpect(jsonPath("$.targetsVisible").value(false))
			.andExpect(jsonPath("$.activities").isNotEmpty())
			.andExpect(jsonPath("$.trend[0].leads").isEmpty());
		mvc.perform(get("/api/marketing/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
			.andExpect(status().isForbidden());
	}

	private ResultActions as(AbstractMockHttpServletRequestBuilder<?> request, String json) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken));
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
