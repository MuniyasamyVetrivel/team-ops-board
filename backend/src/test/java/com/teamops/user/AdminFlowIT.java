package com.teamops.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 4 end-to-end against MySQL through the real HTTP layer: user lifecycle, access changes taking effect on the
 * next request, department management and manager scope, and the team directory. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-seed.enabled=false")
class AdminFlowIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private final List<Long> departments = new ArrayList<>();

	private Long adminId;

	private String adminToken;

	@BeforeEach
	void signInAsSuperAdmin() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId), IntegrationUsers.PASSWORD);
	}

	@AfterEach
	void cleanUp() {
		users.deleteAll();
		departments.forEach(departmentRepository::deleteById);
	}

	@Test
	void userLifecycleThroughTheApi() throws Exception {
		Long itId = departmentRepository.findByCode("IT").orElseThrow().getId();
		String email = "it-" + UUID.randomUUID() + "@teamops.local";

		String created = as(adminToken, post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"Welcome123","firstName":"Asha","lastName":"Iyer","jobTitle":"Analyst",
				 "departmentId":%d,"reportsToId":%d,"roles":["EMPLOYEE"],"permissions":[]}
				""".formatted(email, itId, adminId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.reportsTo.id").value(adminId))
			.andExpect(jsonPath("$.weeklyCapacityHours").value(40.0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long userId = ((Number) JsonPath.read(created, "$.id")).longValue();
		users.track(userId, email);

		// Search finds the new user; the employee can sign in.
		as(adminToken, get("/api/users").param("search", email)).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].department.code").value("IT"))
			.andExpect(jsonPath("$.content[0].roles[0]").value("EMPLOYEE"));
		String userToken = login(email, "Welcome123");
		as(userToken, get("/api/auth/me")).andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.hasItem("MARKETING_VIEW"))));

		// Granting a permission takes effect on the user's very next request, with the same token.
		as(adminToken, put("/api/users/" + userId + "/access").contentType(MediaType.APPLICATION_JSON)
			.content("{\"roles\":[\"EMPLOYEE\"],\"permissions\":[\"MARKETING_VIEW\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.directPermissions[0]").value("MARKETING_VIEW"));
		as(userToken, get("/api/auth/me")).andExpect(jsonPath("$.permissions",
				org.hamcrest.Matchers.hasItem("MARKETING_VIEW")));
		assertThat(auditCount("USER_ACCESS_CHANGED", userId)).isEqualTo(1);

		// Profile edits are audited field by field.
		as(adminToken, put("/api/users/" + userId).contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","firstName":"Asha","lastName":"Iyer","jobTitle":"Senior Analyst",
				 "departmentId":%d,"weeklyCapacityHours":32}
				""".formatted(email, itId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobTitle").value("Senior Analyst"))
			.andExpect(jsonPath("$.reportsTo").doesNotExist());
		assertThat(auditCount("USER_UPDATED", userId)).isEqualTo(1);

		// Disabling blocks the existing token and new logins; enabling restores access.
		as(adminToken, post("/api/users/" + userId + "/disable")).andExpect(jsonPath("$.status").value("DISABLED"));
		as(userToken, get("/api/auth/me")).andExpect(status().isUnauthorized());
		loginAttempt(email, "Welcome123").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
		as(adminToken, post("/api/users/" + userId + "/enable")).andExpect(jsonPath("$.status").value("ACTIVE"));

		// Password reset: old password stops working, new one works.
		as(adminToken, post("/api/users/" + userId + "/reset-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"newPassword\":\"Changed456\"}")).andExpect(status().isNoContent());
		loginAttempt(email, "Welcome123").andExpect(status().isUnauthorized());
		login(email, "Changed456");
	}

	@Test
	void adminCannotLockThemselvesOut() throws Exception {
		as(adminToken, post("/api/users/" + adminId + "/disable")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
		as(adminToken, put("/api/users/" + adminId + "/access").contentType(MediaType.APPLICATION_JSON)
			.content("{\"roles\":[\"EMPLOYEE\"]}")).andExpect(status().isForbidden());
	}

	@Test
	void departmentsAndManagerScope() throws Exception {
		String code = "IT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String createdDepartment = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Integration %s\",\"code\":\"%s\",\"description\":\"Temporary\"}".formatted(code,
					code)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value(code))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long departmentId = ((Number) JsonPath.read(createdDepartment, "$.id")).longValue();
		departments.add(departmentId);

		// A manager whose primary department is the new one, and an IT employee to add as a secondary member.
		Long managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		Long itEmployee = users.create("IT", RoleCodes.EMPLOYEE);
		String managerToken = login(users.email(managerId), IntegrationUsers.PASSWORD);

		as(managerToken, put("/api/departments/%d/members/%d".formatted(departmentId, itEmployee))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"MEMBER\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.members[?(@.user.id == %d)].membership".formatted(itEmployee)).value("MEMBER"));

		// Managers cannot hand out MANAGER memberships, or touch departments outside their scope.
		as(managerToken, put("/api/departments/%d/members/%d".formatted(departmentId, itEmployee))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"MANAGER\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CANNOT_ASSIGN_MANAGER"));
		Long itId = departmentRepository.findByCode("IT").orElseThrow().getId();
		as(managerToken, put("/api/departments/%d/members/%d".formatted(itId, managerId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"MEMBER\"}")).andExpect(status().isForbidden());

		// The list reports member counts; a department with active users cannot be deactivated.
		as(adminToken, get("/api/departments"))
			.andExpect(jsonPath("$[?(@.id == %d)].memberCount".formatted(departmentId)).value(1))
			.andExpect(jsonPath("$[?(@.id == %d)].secondaryMemberCount".formatted(departmentId)).value(1));
		as(adminToken, put("/api/departments/" + departmentId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Integration %s\",\"managerId\":%d,\"status\":\"INACTIVE\"}".formatted(code,
					managerId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEPARTMENT_HAS_ACTIVE_USERS"));

		as(managerToken, delete("/api/departments/%d/members/%d".formatted(departmentId, itEmployee)))
			.andExpect(status().isOk());
		assertThat(auditCount("DEPARTMENT_MEMBER_REMOVED", departmentId)).isEqualTo(1);

		// Team profile: the manager may see work of their department's people, an outsider employee may not.
		as(managerToken, get("/api/team/" + managerId)).andExpect(jsonPath("$.canViewWork").value(true));
		String employeeToken = login(users.email(itEmployee), IntegrationUsers.PASSWORD);
		as(employeeToken, get("/api/team/" + managerId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.canViewWork").value(false))
			.andExpect(jsonPath("$.member.department.code").value(code));
		as(employeeToken, get("/api/team").param("departmentId", departmentId.toString()))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions loginAttempt(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private String login(String email, String password) throws Exception {
		String body = loginAttempt(email, password).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private int auditCount(String action, Long entityId) {
		Integer count = jdbc.queryForObject("select count(*) from audit_logs where action = ? and entity_id = ?",
				Integer.class, action, entityId);
		return count == null ? 0 : count;
	}

}
