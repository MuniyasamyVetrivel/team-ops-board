package com.teamops.marketing.backlink;

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
 * Phase 17 end-to-end against MySQL: backlinks with their stage rules (the dates each status needs, in order, not in
 * the future, a URL once live), the per-date month lock (and the Super Admin exception), status moves that date the
 * new stage, versioned and audited changes, duplicate protection, the monthly summary and history by stage, the
 * Backlinks target counting live links, CSV import and export. Backlinks are owned by a throwaway user and the target
 * actual is compared before and after, so seeded data never changes the checks. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class BacklinkFlowIT {

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

	/** Lower case: domains and URLs are compared as the server normalises them. */
	private String nonce;

	private LocalDate today;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		editorId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "BACKLINK_VIEW", "BACKLINK_EDIT", "TARGET_VIEW");
		Long viewerId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "BACKLINK_VIEW");
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		editorToken = login(users.email(editorId));
		viewerToken = login(users.email(viewerId));
		adminToken = login(users.email(adminId));
		nonce = "it" + Long.toHexString(System.nanoTime());
		today = calendar.today();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM backlinks WHERE owner_id = ? OR created_by IN (?, ?)", editorId, editorId, adminId);
		jdbc.update("""
				DELETE FROM audit_logs WHERE actor_id IN (?, ?) AND (action LIKE 'BACKLINK_%' OR action = 'CSV_IMPORTED')
				""", editorId, adminId);
		users.deleteAll();
	}

	@Test
	void backlinksMoveThroughTheirStagesAndFeedTheMonthlyTracking() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(today);
		BigDecimal liveBefore = actual("BACKLINKS", current);

		// What a backlink may not be.
		create("{\"linkUrl\":\"%s\",\"linkType\":\"GUEST_POST\"}".formatted(link(1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TARGET_REQUIRED"));
		create(json(link(1), null, "APPROVED", "\"submittedDate\":\"" + today + "\"")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MISSING_DATE"))
			.andExpect(jsonPath("$.message").value("An approved backlink needs its approved date"));
		create(json(null, "www.Example-" + nonce + ".com", "LIVE",
				"\"submittedDate\":\"" + today + "\",\"liveDate\":\"" + today + "\"")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("LINK_URL_REQUIRED"));
		create(json(link(1), null, "SUBMITTED", "\"submittedDate\":\"" + today.plusDays(1) + "\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FUTURE_DATE"));
		LocalDate closed = current.plusMonths(-3).firstDay().plusDays(9);
		create(json(link(1), null, "SUBMITTED", "\"submittedDate\":\"" + closed + "\"")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));

		// A prospect (domain only, normalised) and two submitted links.
		create(json(null, "www.Prospect-" + nonce + ".COM", null, null)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value(Matchers.startsWith("BLK-")))
			.andExpect(jsonPath("$.status").value("PROSPECTED"))
			.andExpect(jsonPath("$.referringDomain").value("prospect-" + nonce + ".com"));
		Long first = id(create(json(link(1), null, "SUBMITTED", "\"submittedDate\":\"" + today + "\""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.referringDomain").value("blog-" + nonce + ".com"))
			.andExpect(jsonPath("$.lockedDates.length()").value(0)), "$.id");
		Long second = id(create(json(link(2), null, "SUBMITTED", "\"submittedDate\":\"" + today + "\""))
			.andExpect(status().isCreated()), "$.id");
		create(json(link(1), null, "SUBMITTED", "\"submittedDate\":\"" + today + "\"")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_BACKLINK"));

		// Status moves date the new stage; going live counts towards the Backlinks target.
		as(editorToken, put("/api/marketing/backlinks/" + first + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"APPROVED\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.approvedDate").value(today.toString()));
		as(editorToken, put("/api/marketing/backlinks/" + first + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"LIVE\"}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(editorToken, put("/api/marketing/backlinks/" + first + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":1,\"status\":\"LIVE\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LIVE"))
			.andExpect(jsonPath("$.liveDate").value(today.toString()));
		as(editorToken, put("/api/marketing/backlinks/" + second + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"REJECTED\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.rejectedDate").value(today.toString()));
		assertThat(jdbc.queryForObject(
				"SELECT details FROM audit_logs WHERE action = 'BACKLINK_STATUS_CHANGED' AND entity_id = ? ORDER BY id DESC LIMIT 1",
				String.class, first)).contains("\"liveDate\"").contains("LIVE");
		assertThat(actual("BACKLINKS", current)).isEqualByComparingTo(liveBefore.add(BigDecimal.ONE));
		as(editorToken, get("/api/marketing/target-types"))
			.andExpect(jsonPath("$[?(@.code == 'BACKLINKS')].automatic").value(Matchers.contains(true)));

		// Brief section 46: the month by stage, today's pipeline, and the target for TARGET_VIEW holders only.
		as(editorToken, get("/api/marketing/backlinks/summary").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.submitted").value(2))
			.andExpect(jsonPath("$.current.approved").value(1))
			.andExpect(jsonPath("$.current.live").value(1))
			.andExpect(jsonPath("$.current.rejected").value(1))
			.andExpect(jsonPath("$.comparison.submitted").value(0))
			.andExpect(jsonPath("$.liveByType[0].linkType").value("GUEST_POST"))
			.andExpect(jsonPath("$.byOwner[0].live").value(1))
			.andExpect(jsonPath("$.pipeline[?(@.status == 'PROSPECTED')].backlinks").value(Matchers.contains(1)))
			.andExpect(jsonPath("$.pipeline[?(@.status == 'LIVE')].backlinks").value(Matchers.contains(1)))
			.andExpect(jsonPath("$.targetsVisible").value(true));
		as(viewerToken, get("/api/marketing/backlinks/summary").param("ownerId", editorId.toString()))
			.andExpect(jsonPath("$.targetsVisible").value(false))
			.andExpect(jsonPath("$.target").isEmpty());
		as(viewerToken, get("/api/marketing/backlinks/trend").param("ownerId", editorId.toString()).param("months", "3"))
			.andExpect(jsonPath("$.months.length()").value(3))
			.andExpect(jsonPath("$.months[2].activity.submitted").value(2))
			.andExpect(jsonPath("$.months[2].activity.live").value(1))
			.andExpect(jsonPath("$.months[2].targetValue").isEmpty())
			.andExpect(jsonPath("$.months[0].activity.submitted").value(0));

		// The list by stage of the month.
		as(viewerToken, get("/api/marketing/backlinks").param("ownerId", editorId.toString())
			.param("month", String.valueOf(current.month()))
			.param("year", String.valueOf(current.year()))
			.param("stage", "LIVE")).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(first));
		as(viewerToken, get("/api/marketing/backlinks").param("ownerId", editorId.toString())
			.param("month", String.valueOf(current.month()))
			.param("year", String.valueOf(current.year()))).andExpect(jsonPath("$.totalElements").value(2));
		as(viewerToken, get("/api/marketing/backlinks").param("search", "prospect-" + nonce))
			.andExpect(jsonPath("$.totalElements").value(1));

		// Details are versioned and audited; a change that breaks the stage rules is refused.
		as(editorToken, put("/api/marketing/backlinks/" + first).contentType(MediaType.APPLICATION_JSON)
			.content(json(link(1), null, "LIVE", "\"version\":2,\"submittedDate\":\"" + today + "\",\"approvedDate\":\"" + today
					+ "\",\"liveDate\":\"" + today + "\",\"anchorText\":\"SAP testing\",\"domainAuthority\":61")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.anchorText").value("SAP testing"))
			.andExpect(jsonPath("$.domainAuthority").value(61));
		assertThat(jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'BACKLINK_UPDATED' AND entity_id = ?",
				String.class, first)).contains("anchorText").contains("domainAuthority");
		as(editorToken, put("/api/marketing/backlinks/" + first).contentType(MediaType.APPLICATION_JSON)
			.content(json(link(1), null, "LIVE", "\"version\":3,\"submittedDate\":\"" + today + "\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MISSING_DATE"));

		as(viewerToken, delete("/api/marketing/backlinks/" + second)).andExpect(status().isForbidden());
		as(editorToken, delete("/api/marketing/backlinks/" + second)).andExpect(status().isNoContent());
	}

	@Test
	void datesInClosedMonthsStayAsCountedButLaterStagesCanStillHappen() throws Exception {
		MarketingPeriod old = MarketingPeriod.of(today).plusMonths(-3);
		LocalDate submitted = old.firstDay().plusDays(4);
		LocalDate live = old.firstDay().plusDays(11);
		BigDecimal oldLive = actual("BACKLINKS", old);

		// Only a Super Admin records a closed month; the editor then sees those dates locked.
		Long late = id(as(adminToken, post("/api/marketing/backlinks").contentType(MediaType.APPLICATION_JSON)
			.content(json(link(7), null, "LIVE", "\"submittedDate\":\"" + submitted + "\",\"liveDate\":\"" + live + "\"")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.lockedDates.length()").value(0)), "$.id");
		assertThat(actual("BACKLINKS", old)).isEqualByComparingTo(oldLive.add(BigDecimal.ONE));
		as(editorToken, get("/api/marketing/backlinks/" + late))
			.andExpect(jsonPath("$.lockedDates").value(Matchers.contains("submittedDate", "liveDate")));

		as(editorToken, put("/api/marketing/backlinks/" + late).contentType(MediaType.APPLICATION_JSON)
			.content(json(link(7), null, "LIVE", "\"version\":0,\"submittedDate\":\"" + submitted + "\",\"liveDate\":\""
					+ live.plusDays(1) + "\""))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"))
			.andExpect(jsonPath("$.message").value(Matchers.startsWith("The live date counts in " + old.label())));
		as(editorToken, delete("/api/marketing/backlinks/" + late)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));

		// Losing the link today only adds a date in an open month, so the old month keeps its count.
		as(editorToken, put("/api/marketing/backlinks/" + late + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"LOST\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.lostDate").value(today.toString()))
			.andExpect(jsonPath("$.liveDate").value(live.toString()));
		assertThat(actual("BACKLINKS", old)).isEqualByComparingTo(oldLive.add(BigDecimal.ONE));
		// Undoing the loss clears a date of this month only.
		as(editorToken, put("/api/marketing/backlinks/" + late + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":1,\"status\":\"LIVE\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.lostDate").isEmpty());
		// Back to submitted would clear the closed month's live date.
		as(editorToken, put("/api/marketing/backlinks/" + late + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":2,\"status\":\"SUBMITTED\"}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));

		as(adminToken, delete("/api/marketing/backlinks/" + late)).andExpect(status().isNoContent());
		assertThat(actual("BACKLINKS", old)).isEqualByComparingTo(oldLive);
	}

	@Test
	void csvImportAddsBacklinksOnceAndExportListsThem() throws Exception {
		String owner = users.email(editorId);
		String header = "target_url,link_url,anchor_text,status,submitted_date,live_date,domain_authority,owner_email\n";

		as(editorToken, multipart("/api/marketing/imports/backlinks/preview").file(csv(header + """
				/it/%1$s,https://blog-%1$s.com/a,SAP testing,LIVE,%2$s,%2$s,58,%3$s
				/it/%1$s,https://blog-%1$s.com/b,SAP testing,LIVE,%2$s,,40,%3$s
				/it/%1$s,https://blog-%1$s.com/c,SAP testing,SUBMITTED,%2$s,,140,%3$s
				not a target,https://blog-%1$s.com/d,SAP testing,SUBMITTED,%2$s,,40,%3$s
				""".formatted(nonce, today, owner)))).andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(3))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].column").value("status"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].column").value("domain_authority"))
			.andExpect(jsonPath("$.invalidRows[2].errors[0].column").value("target_url"));

		MockMultipartFile file = csv(header + "/it/%1$s,https://blog-%1$s.com/a,SAP testing,LIVE,%2$s,%2$s,58,%3$s\n"
			.formatted(nonce, today, owner));
		String preview = as(editorToken, multipart("/api/marketing/imports/backlinks/preview").file(file))
			.andReturn().getResponse().getContentAsString();
		as(editorToken, multipart("/api/marketing/imports/backlinks/commit").file(file)
			.param("checksum", (String) JsonPath.read(preview, "$.checksum"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(1));
		assertThat(jdbc.queryForMap("SELECT provider, referring_domain, status, owner_id FROM backlinks WHERE link_url = ?",
				"https://blog-" + nonce + ".com/a")).containsEntry("provider", "CSV")
			.containsEntry("referring_domain", "blog-" + nonce + ".com")
			.containsEntry("status", "LIVE")
			.containsEntry("owner_id", editorId);

		// The same link to the same target is already recorded.
		as(editorToken, multipart("/api/marketing/imports/backlinks/preview").file(file))
			.andExpect(jsonPath("$.validCount").value(0))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].column").value("link_url"))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message").value("This page already links to /it/" + nonce));

		String csv = as(viewerToken, get("/api/marketing/backlinks/export").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("blog-" + nonce + ".com,https://blog-" + nonce + ".com/a,/it/" + nonce)
			.contains(",GUEST_POST,LIVE," + today + ",," + today + ",,,58,");
	}

	private BigDecimal actual(String typeCode, MarketingPeriod period) {
		TargetType type = typeRepository.findAllByOrderByPositionAscNameAsc()
			.stream()
			.filter(t -> t.getCode().equals(typeCode))
			.findFirst()
			.orElseThrow();
		return targetActuals.computed(type, period, period).get(period);
	}

	private String link(int n) {
		return "https://blog-" + nonce + ".com/sap-testing-" + n;
	}

	/** A backlink to this test's own target path, owned by the editor. */
	private String json(String linkUrl, String domain, String status, String extra) {
		StringBuilder json = new StringBuilder("{\"targetUrl\":\"/it/" + nonce + "\",\"linkType\":\"GUEST_POST\",\"ownerId\":" + editorId);
		if (linkUrl != null) {
			json.append(",\"linkUrl\":\"").append(linkUrl).append('"');
		}
		if (domain != null) {
			json.append(",\"referringDomain\":\"").append(domain).append('"');
		}
		if (status != null) {
			json.append(",\"status\":\"").append(status).append('"');
		}
		if (extra != null) {
			json.append(',').append(extra);
		}
		return json.append('}').toString();
	}

	private ResultActions create(String json) throws Exception {
		return as(editorToken, post("/api/marketing/backlinks").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static MockMultipartFile csv(String text) {
		return new MockMultipartFile("file", "backlinks.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
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
