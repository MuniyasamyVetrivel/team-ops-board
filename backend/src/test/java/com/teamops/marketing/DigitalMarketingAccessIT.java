package com.teamops.marketing;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * The Digital Marketing department rule (V41) against MySQL: every member of the department holds the whole Digital
 * Marketing area without any grant of their own, keeps the Super Admin-only tools out of reach, and loses it all when
 * moved to another department. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class DigitalMarketingAccessIT {

	private static final String[] MARKETING_PERMISSIONS = { "MARKETING_VIEW", "MARKETING_EDIT", "SEO_VIEW", "SEO_EDIT",
			"CAMPAIGN_VIEW", "CAMPAIGN_EDIT", "TARGET_VIEW", "TARGET_EDIT", "LEAD_VIEW", "LEAD_EDIT", "BACKLINK_VIEW",
			"BACKLINK_EDIT", "CONTENT_VIEW", "CONTENT_EDIT", "CELEBRATIONS_EDIT" };

	private static final List<String> MARKETING_PAGES = List.of("/api/marketing/dashboard",
			"/api/marketing/reports/monthly", "/api/marketing/pages", "/api/marketing/keywords",
			"/api/marketing/targets", "/api/marketing/email-campaigns", "/api/marketing/paid-campaigns",
			"/api/marketing/leads", "/api/marketing/backlinks", "/api/marketing/content", "/api/marketing/activities",
			"/api/celebrations/upcoming", "/api/celebrations/employees", "/api/celebrations/important-days",
			"/api/celebrations/events", "/api/celebrations/linkedin-posts");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	private IntegrationUsers users;

	@BeforeEach
	void setUp() {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
	}

	@AfterEach
	void cleanUp() {
		users.deleteAll();
	}

	@Test
	void digitalMarketingMembersGetTheWholeMarketingAreaFromTheirDepartment() throws Exception {
		Long memberId = users.create("DM", RoleCodes.EMPLOYEE);
		String member = login(users.email(memberId));

		as(member, get("/api/auth/me")).andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions", hasItems(MARKETING_PERMISSIONS)))
			.andExpect(jsonPath("$.permissions", not(hasItem("FINANCE_VIEW"))))
			.andExpect(jsonPath("$.permissions", not(hasItem("VENDOR_VIEW"))))
			.andExpect(jsonPath("$.permissions", not(hasItem("BENCH_VIEW"))))
			.andExpect(jsonPath("$.permissions", not(hasItem("BACKUP_VIEW"))))
			.andExpect(jsonPath("$.permissions", not(hasItem("AUDIT_VIEW"))))
			.andExpect(jsonPath("$.permissions", not(hasItem("RECYCLE_BIN_MANAGE"))));
		for (String path : MARKETING_PAGES) {
			as(member, get(path)).andExpect(status().isOk());
		}
		// Every dashboard section, targets included.
		as(member, get("/api/marketing/dashboard")).andExpect(jsonPath("$.targetsVisible").value(true))
			.andExpect(jsonPath("$.seo").exists())
			.andExpect(jsonPath("$.leads").exists())
			.andExpect(jsonPath("$.email").exists())
			.andExpect(jsonPath("$.backlinks").exists())
			.andExpect(jsonPath("$.content").exists());
		// Super Admin-only tools stay closed.
		as(member, get("/api/admin/audit-logs")).andExpect(status().isForbidden());
		as(member, get("/api/recycle-bin")).andExpect(status().isForbidden());
	}

	@Test
	void movingOutOfDigitalMarketingTakesTheAccessAway() throws Exception {
		Long memberId = users.create("DM", RoleCodes.EMPLOYEE);
		String member = login(users.email(memberId));
		as(member, get("/api/celebrations/upcoming")).andExpect(status().isOk());

		userRepository.findById(memberId).ifPresent(user -> {
			user.setDepartment(departmentRepository.findByCode("HR").orElseThrow());
			userRepository.save(user);
		});

		// Permissions are resolved from the database on every request, so the same token loses them at once.
		as(member, get("/api/auth/me")).andExpect(jsonPath("$.permissions", not(hasItem("MARKETING_VIEW"))))
			.andExpect(jsonPath("$.permissions", not(hasItem("CELEBRATIONS_EDIT"))));
		for (String path : MARKETING_PAGES) {
			as(member, get(path)).andExpect(status().isForbidden());
		}
	}

	@Test
	void otherDepartmentsDoNotGetMarketingAccess() throws Exception {
		for (String department : List.of("HR", "IT", "LEADERSHIP")) {
			Long id = users.create(department, RoleCodes.EMPLOYEE);
			String token = login(users.email(id));
			as(token, get("/api/auth/me")).andExpect(jsonPath("$.permissions", not(hasItem("MARKETING_VIEW"))));
			for (String path : MARKETING_PAGES) {
				as(token, get(path)).andExpect(status().isForbidden());
			}
		}
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
