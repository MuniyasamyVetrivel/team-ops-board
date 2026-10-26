package com.teamops.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Administration → Departments against MySQL: the Leadership department from V43, and a Super Admin creating, editing
 * and deactivating a department with audit entries. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class DepartmentAdminIT {

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

	private String adminToken;

	@BeforeEach
	void signInAsSuperAdmin() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		adminToken = login(users.email(users.create("IT", RoleCodes.SUPER_ADMIN)));
	}

	@AfterEach
	void cleanUp() {
		users.deleteAll();
		departments.forEach(departmentRepository::deleteById);
	}

	@Test
	void leadershipIsAPlainActiveDepartment() throws Exception {
		Department leadership = departmentRepository.findByCode("LEADERSHIP").orElseThrow();
		assertThat(leadership.getName()).isEqualTo("Leadership");
		assertThat(leadership.getStatus()).isEqualTo(DepartmentStatus.ACTIVE);
		as(adminToken, get("/api/departments")).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.code == 'LEADERSHIP')].status").value("ACTIVE"));

		// No permissions or tools come with it, so its people get nothing beyond their roles.
		assertThat(count("select count(*) from department_permissions where department_id = ?", leadership.getId()))
			.isZero();
		assertThat(count("select count(*) from department_modules where department_id = ?", leadership.getId()))
			.isZero();
		String member = login(users.email(users.create("LEADERSHIP", RoleCodes.EMPLOYEE)));
		as(member, get("/api/auth/me")).andExpect(jsonPath("$.department.code").value("LEADERSHIP"))
			.andExpect(jsonPath("$.permissions", not(hasItem("MARKETING_VIEW"))))
			.andExpect(jsonPath("$.permissions", not(hasItem("CELEBRATIONS_EDIT"))));
		as(member, get("/api/celebrations/upcoming")).andExpect(status().isForbidden());
	}

	@Test
	void superAdminCreatesEditsAndDeactivatesADepartment() throws Exception {
		Long managerId = users.create("IT", RoleCodes.DEPARTMENT_MANAGER);
		String code = "QA_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String created = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"Quality %s","code":"%s","description":"Testing","managerId":%d,"status":"ACTIVE"}
					""".formatted(code, code.toLowerCase(), managerId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.manager.id").value(managerId))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(created, "$.id")).longValue();
		departments.add(id);
		assertThat(auditCount("DEPARTMENT_CREATED", id)).isEqualTo(1);

		// Codes and names stay unique.
		as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Another\",\"code\":\"%s\"}".formatted(code)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEPARTMENT_CODE_IN_USE"));
		as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Leadership\",\"code\":\"LEAD_%s\"}".formatted(code)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEPARTMENT_NAME_IN_USE"));

		// Edit, then deactivate: nobody has it as their primary department.
		as(adminToken, put("/api/departments/" + id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Quality Assurance %s\",\"description\":null,\"managerId\":null,\"status\":\"ACTIVE\"}"
				.formatted(code)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Quality Assurance " + code))
			.andExpect(jsonPath("$.manager").doesNotExist());
		as(adminToken, put("/api/departments/" + id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Quality Assurance %s\",\"status\":\"INACTIVE\"}".formatted(code)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("INACTIVE"));
		as(adminToken, get("/api/departments"))
			.andExpect(jsonPath("$[?(@.id == %d)].status".formatted(id)).value("INACTIVE"));
		assertThat(auditCount("DEPARTMENT_UPDATED", id)).isEqualTo(2);

		// Employees can neither create nor change departments.
		String employee = login(users.email(users.create("IT", RoleCodes.EMPLOYEE)));
		as(employee, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Nope\",\"code\":\"NOPE\"}")).andExpect(status().isForbidden());
		as(employee, put("/api/departments/" + id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Nope\",\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
	}

	@Test
	void aDepartmentCanBeCreatedInactive() throws Exception {
		String code = "ARCH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String created = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Archive %s\",\"code\":\"%s\",\"status\":\"INACTIVE\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("INACTIVE"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		departments.add(((Number) JsonPath.read(created, "$.id")).longValue());
	}

	private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
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

	private int auditCount(String action, Long entityId) {
		return count("select count(*) from audit_logs where action = ? and entity_id = ?", action, entityId);
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}

}
