package com.teamops.marketing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Phase 18 end-to-end against MySQL: content with its publishing rules (URL and publication date once live, none
 * before, never in the future), status moves that date publication and refresh, the month lock on where an item
 * counts (and the Super Admin exception), content that leads name staying published, the monthly summary and history
 * (planned, published, leads) and the Blogs Published target counting live blog posts, duplicate URLs, and export.
 * Content is owned by a throwaway user and the target actual is compared before and after, so seeded data never
 * changes the checks. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class ContentFlowIT {

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
		editorId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "CONTENT_VIEW", "CONTENT_EDIT", "TARGET_VIEW",
				"LEAD_VIEW", "LEAD_EDIT");
		Long viewerId = users.create("DM", RoleCodes.EMPLOYEE, "MARKETING_VIEW", "CONTENT_VIEW");
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		editorToken = login(users.email(editorId));
		viewerToken = login(users.email(viewerId));
		adminToken = login(users.email(adminId));
		nonce = "it" + Long.toHexString(System.nanoTime());
		today = calendar.today();
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM marketing_leads WHERE owner_id = ? OR created_by IN (?, ?)", editorId, editorId, adminId);
		jdbc.update("DELETE FROM content_items WHERE owner_id = ? OR created_by IN (?, ?)", editorId, editorId, adminId);
		jdbc.update("DELETE FROM files WHERE uploaded_by IN (?, ?)", editorId, adminId);
		jdbc.update("""
				DELETE FROM audit_logs WHERE actor_id IN (?, ?) AND (action LIKE 'CONTENT_%' OR action LIKE 'LEAD_%')
				""", editorId, adminId);
		users.deleteAll();
	}

	@Test
	void contentIsPlannedPublishedAndCountedTowardsTheBlogTarget() throws Exception {
		MarketingPeriod current = MarketingPeriod.of(today);
		BigDecimal blogsBefore = actual("BLOGS_PUBLISHED", current);
		LocalDate yesterday = today.minusDays(1);

		// What content may not be.
		create(json("Draft", "BLOG", "DRAFT", url(0), "\"publicationDate\":\"" + today + "\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DATE_NOT_ALLOWED"));
		create(json("No URL", "BLOG", "PUBLISHED", null, "\"publicationDate\":\"" + today + "\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CONTENT_URL_REQUIRED"));
		create(json("Tomorrow", "BLOG", "PUBLISHED", url(0), "\"publicationDate\":\"" + today.plusDays(1) + "\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FUTURE_DATE"));
		create(json("Old", "BLOG", "PUBLISHED", url(0),
				"\"publicationDate\":\"" + current.plusMonths(-3).firstDay() + "\"")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));
		create(json("Bad URL", "BLOG", "IDEA", "not a url", null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_URL"));

		// A blog planned for this month, a published blog and a published case study.
		Long planned = id(create(json("Planned post", "BLOG", "PLANNED", null, "\"plannedDate\":\"" + today + "\""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PLANNED"))
			.andExpect(jsonPath("$.publicationDate").isEmpty()), "$.id");
		Long post = id(create(json("Published post", "BLOG", "PUBLISHED", url(1), "\"publicationDate\":\"" + yesterday + "\""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.leads").value(0))
			.andExpect(jsonPath("$.publicationLocked").value(false)), "$.id");
		Long caseStudy = id(create(json("Case study", "CASE_STUDY", "PUBLISHED", url(2), "\"publicationDate\":\"" + today + "\""))
			.andExpect(status().isCreated()), "$.id");
		create(json("Copy", "BLOG", "IDEA", url(1).toUpperCase(), null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_URL"));

		// Publishing needs the URL; then the status move dates it today.
		as(editorToken, put("/api/marketing/content/" + planned + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"PUBLISHED\"}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CONTENT_URL_REQUIRED"));
		as(editorToken, put("/api/marketing/content/" + planned).contentType(MediaType.APPLICATION_JSON)
			.content(json("Planned post", "BLOG", "IN_PROGRESS", url(3), "\"version\":0,\"plannedDate\":\"" + today + "\"")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
		as(editorToken, put("/api/marketing/content/" + planned + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":1,\"status\":\"PUBLISHED\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.publicationDate").value(today.toString()));
		assertThat(jdbc.queryForObject(
				"SELECT details FROM audit_logs WHERE action = 'CONTENT_STATUS_CHANGED' AND entity_id = ?", String.class,
				planned)).contains("publicationDate").contains("PUBLISHED");

		// Blogs Published counts live blog posts only (not the case study); yesterday may be last month.
		boolean postThisMonth = MarketingPeriod.of(yesterday).equals(current);
		assertThat(actual("BLOGS_PUBLISHED", current))
			.isEqualByComparingTo(blogsBefore.add(BigDecimal.valueOf(postThisMonth ? 2 : 1)));
		as(editorToken, get("/api/marketing/target-types"))
			.andExpect(jsonPath("$[?(@.code == 'BLOGS_PUBLISHED')].automatic").value(Matchers.contains(true)));

		// A blog lead from yesterday's post.
		as(editorToken, post("/api/marketing/leads").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"Anita Rao","source":"BLOG","leadDate":"%s","contentItemId":%d,"ownerId":%d}
					""".formatted(yesterday, post, editorId))).andExpect(status().isCreated());
		as(viewerToken, get("/api/marketing/content/" + post)).andExpect(jsonPath("$.leads").value(1));

		// Brief section 48: planned, published, leads; targets only for TARGET_VIEW holders.
		as(editorToken, get("/api/marketing/content/summary").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.plannedBlogs").value(1))
			.andExpect(jsonPath("$.current.publishedBlogs").value(postThisMonth ? 2 : 1))
			.andExpect(jsonPath("$.current.publishedAll").value(postThisMonth ? 3 : 2))
			.andExpect(jsonPath("$.current.leads").value(postThisMonth ? 1 : 0))
			.andExpect(jsonPath("$.pipeline[?(@.status == 'PUBLISHED')].items").value(Matchers.contains(3)))
			.andExpect(jsonPath("$.publishedByType[?(@.contentType == 'CASE_STUDY')].published").value(Matchers.contains(1)))
			.andExpect(jsonPath("$.targetsVisible").value(true));
		as(viewerToken, get("/api/marketing/content/summary").param("ownerId", editorId.toString())
			.param("month", String.valueOf(MarketingPeriod.of(yesterday).month()))
			.param("year", String.valueOf(MarketingPeriod.of(yesterday).year())))
			.andExpect(jsonPath("$.targetsVisible").value(false))
			.andExpect(jsonPath("$.blogTarget").isEmpty())
			.andExpect(jsonPath("$.topContent[0].id").value(post))
			.andExpect(jsonPath("$.topContent[0].leads").value(1));
		as(viewerToken, get("/api/marketing/content/trend").param("ownerId", editorId.toString()).param("months", "3"))
			.andExpect(jsonPath("$.months.length()").value(3))
			.andExpect(jsonPath("$.months[2].figures.plannedBlogs").value(1))
			.andExpect(jsonPath("$.months[2].targetValue").isEmpty());

		// The list by publication month and type.
		as(viewerToken, get("/api/marketing/content").param("ownerId", editorId.toString())
			.param("month", String.valueOf(current.month()))
			.param("year", String.valueOf(current.year()))
			.param("dateField", "PUBLISHED")
			.param("contentType", "BLOG")).andExpect(jsonPath("$.totalElements").value(postThisMonth ? 2 : 1));

		// Content that leads name stays published, no later than the first lead, and is kept.
		as(editorToken, put("/api/marketing/content/" + post + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"DRAFT\"}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTENT_HAS_LEADS"));
		as(editorToken, put("/api/marketing/content/" + post).contentType(MediaType.APPLICATION_JSON)
			.content(json("Published post", "BLOG", "PUBLISHED", url(1), "\"version\":0,\"publicationDate\":\"" + today + "\"")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTENT_HAS_LEADS"));
		as(editorToken, delete("/api/marketing/content/" + post)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTENT_HAS_LEADS"));

		// A refresh keeps the publication date and adds the refreshed date.
		as(editorToken, put("/api/marketing/content/" + post + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"UPDATED\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.publicationDate").value(yesterday.toString()))
			.andExpect(jsonPath("$.refreshedDate").value(today.toString()));

		as(viewerToken, delete("/api/marketing/content/" + caseStudy)).andExpect(status().isForbidden());
		as(editorToken, delete("/api/marketing/content/" + caseStudy)).andExpect(status().isNoContent());

		String csv = as(viewerToken, get("/api/marketing/content/export").param("ownerId", editorId.toString()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains(nonce + " Published post,BLOG,UPDATED," + url(1));
	}

	@Test
	void whereContentCountsStaysPutInClosedMonths() throws Exception {
		MarketingPeriod old = MarketingPeriod.of(today).plusMonths(-3);
		LocalDate published = old.firstDay().plusDays(6);
		BigDecimal oldBlogs = actual("BLOGS_PUBLISHED", old);

		// Only a Super Admin publishes into a closed month; the editor then sees it locked.
		Long item = id(as(adminToken, post("/api/marketing/content").contentType(MediaType.APPLICATION_JSON)
			.content(json("Old post", "BLOG", "PUBLISHED", url(9), "\"publicationDate\":\"" + published + "\"")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.publicationLocked").value(false)), "$.id");
		assertThat(actual("BLOGS_PUBLISHED", old)).isEqualByComparingTo(oldBlogs.add(BigDecimal.ONE));
		as(editorToken, get("/api/marketing/content/" + item)).andExpect(jsonPath("$.publicationLocked").value(true));

		as(editorToken, put("/api/marketing/content/" + item + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"status\":\"DRAFT\"}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"))
			.andExpect(jsonPath("$.message").value(Matchers.startsWith("This content counts as published in " + old.label())));
		as(editorToken, put("/api/marketing/content/" + item).contentType(MediaType.APPLICATION_JSON)
			.content(json("Old post", "CASE_STUDY", "PUBLISHED", url(9), "\"version\":0,\"publicationDate\":\"" + published + "\"")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));
		as(editorToken, delete("/api/marketing/content/" + item)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MONTH_LOCKED"));

		// Details and a refresh today stay possible.
		as(editorToken, put("/api/marketing/content/" + item).contentType(MediaType.APPLICATION_JSON)
			.content(json("Old post, retitled", "BLOG", "PUBLISHED", url(9),
					"\"version\":0,\"publicationDate\":\"" + published + "\",\"organicTraffic\":1200")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.organicTraffic").value(1200));
		as(editorToken, put("/api/marketing/content/" + item + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":1,\"status\":\"UPDATED\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshedDate").value(today.toString()));
		assertThat(actual("BLOGS_PUBLISHED", old)).isEqualByComparingTo(oldBlogs.add(BigDecimal.ONE));

		as(adminToken, delete("/api/marketing/content/" + item)).andExpect(status().isNoContent());
		assertThat(actual("BLOGS_PUBLISHED", old)).isEqualByComparingTo(oldBlogs);
	}

	@Test
	void attachmentsAreCheckedStoredAndRemovedWithTheItem() throws Exception {
		Long item = id(create(json("Brief", "BLOG", "IN_PROGRESS", null, "\"plannedDate\":\"" + today + "\""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.attachments").value(0)), "$.id");
		String path = "/api/marketing/content/" + item + "/attachments";

		// Checked and stored like every other upload: the type comes from the name, not the browser.
		String list = as(editorToken, multipart(path).file(file("outline.txt", "text/html", "Outline")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].fileName").value("outline.txt"))
			.andExpect(jsonPath("$[0].contentType").value("text/plain"))
			.andReturn().getResponse().getContentAsString();
		Long fileId = ((Number) JsonPath.read(list, "$[0].fileId")).longValue();
		as(editorToken, multipart(path).file(file("run.exe", "application/octet-stream", "x")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
		as(viewerToken, multipart(path).file(file("notes.txt", "text/plain", "x"))).andExpect(status().isForbidden());
		as(editorToken, multipart(path).file(file("images.zip", "application/zip", "PK"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
		as(viewerToken, get("/api/marketing/content/" + item)).andExpect(jsonPath("$.attachments").value(2));

		// Viewers download, always as an attachment.
		as(viewerToken, get(path + "/" + fileId)).andExpect(status().isOk())
			.andExpect(content().string("Outline"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("attachment")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		as(viewerToken, delete(path + "/" + fileId)).andExpect(status().isForbidden());
		as(editorToken, delete(path + "/" + fileId)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		as(viewerToken, get(path + "/" + fileId)).andExpect(status().isNotFound());

		// Deleting the item removes its files too.
		Long other = ((Number) JsonPath.read(as(viewerToken, get(path)).andReturn().getResponse().getContentAsString(),
				"$[0].fileId")).longValue();
		as(editorToken, delete("/api/marketing/content/" + item)).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id IN (?, ?)", Integer.class, fileId, other))
			.isZero();
	}

	private static MockMultipartFile file(String name, String type, String text) {
		return new MockMultipartFile("file", name, type, text.getBytes(StandardCharsets.UTF_8));
	}

	private BigDecimal actual(String typeCode, MarketingPeriod period) {
		TargetType type = typeRepository.findAllByOrderByPositionAscNameAsc()
			.stream()
			.filter(t -> t.getCode().equals(typeCode))
			.findFirst()
			.orElseThrow();
		return targetActuals.computed(type, period, period).get(period);
	}

	private String url(int n) {
		return "https://www.example.com/blog/" + nonce + "-" + n;
	}

	/** A content item owned by the editor, titled with this test's nonce. */
	private String json(String title, String type, String status, String url, String extra) {
		StringBuilder json = new StringBuilder("{\"title\":\"" + nonce + " " + title + "\",\"contentType\":\"" + type
				+ "\",\"status\":\"" + status + "\",\"ownerId\":" + editorId);
		if (url != null) {
			json.append(",\"url\":\"").append(url).append('"');
		}
		if (extra != null) {
			json.append(',').append(extra);
		}
		return json.append('}').toString();
	}

	private ResultActions create(String json) throws Exception {
		return as(editorToken, post("/api/marketing/content").contentType(MediaType.APPLICATION_JSON).content(json));
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
