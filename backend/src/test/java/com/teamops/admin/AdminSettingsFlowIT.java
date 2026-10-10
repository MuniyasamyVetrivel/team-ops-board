package com.teamops.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

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
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 21 end-to-end against MySQL: admin settings (validation, optimistic locking, effect, audit), the role
 * permission matrix and its rules, approval type configuration, and the audit log viewer's filters and export. Every
 * shared record it touches (a setting, the EMPLOYEE role) is restored afterwards. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class AdminSettingsFlowIT {

	private static final String WINDOW = "workload.windowDays";

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

	private Long adminId;

	private Long employeeId;

	private String adminToken;

	private String employeeToken;

	private String accessAdminToken;

	private String originalWindow;

	private Long employeeRoleId;

	private List<Long> employeeRolePermissions;

	private final List<Long> approvalTypes = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		originalWindow = jdbc.queryForObject("SELECT setting_value FROM app_settings WHERE setting_key = ?", String.class,
				WINDOW);
		employeeRoleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'EMPLOYEE'", Long.class);
		employeeRolePermissions = jdbc.queryForList("SELECT permission_id FROM role_permissions WHERE role_id = ?",
				Long.class, employeeRoleId);

		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		employeeId = users.create("IT", RoleCodes.EMPLOYEE);
		Long accessAdminId = users.create("IT", RoleCodes.DEPARTMENT_MANAGER, "PERMISSION_MANAGE");
		adminToken = login(users.email(adminId));
		employeeToken = login(users.email(employeeId));
		accessAdminToken = login(users.email(accessAdminId));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("UPDATE app_settings SET setting_value = ?, updated_by = NULL WHERE setting_key = ?", originalWindow,
				WINDOW);
		jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", employeeRoleId);
		employeeRolePermissions.forEach(permissionId -> jdbc
			.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)", employeeRoleId, permissionId));
		approvalTypes.forEach(id -> jdbc.update("DELETE FROM approval_types WHERE id = ?", id));
		List<Long> ids = new ArrayList<>(List.of(adminId, employeeId));
		ids.forEach(id -> jdbc.update("DELETE FROM audit_logs WHERE actor_id = ?", id));
		users.deleteAll();
	}

	@Test
	@SuppressWarnings("unchecked")
	void settingsAreValidatedLockedAppliedAndAudited() throws Exception {
		String list = get(adminToken, "/api/admin/settings").andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.key == 'workload.windowDays')].group").value(hasItem("Workload")))
			.andExpect(jsonPath("$[?(@.key == 'users.defaultWeeklyCapacityHours')].value").value(hasItem("40")))
			.andExpect(jsonPath("$[?(@.key == 'upload.maxSizeMb')].max").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		int version = ((List<Integer>) JsonPath.read(list, "$[?(@.key == 'workload.windowDays')].version")).get(0);
		String newValue = "14".equals(originalWindow) ? "21" : "14";

		as(adminToken, put("/api/admin/settings/" + WINDOW), "{\"version\":%d,\"value\":\"%s\"}".formatted(version, newValue))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.value").value(newValue))
			.andExpect(jsonPath("$.version").value(version + 1))
			.andExpect(jsonPath("$.updatedBy.id").value(adminId));
		// The workload calculation reads the new window straight away.
		get(adminToken, "/api/reports/workload").andExpect(status().isOk())
			.andExpect(jsonPath("$.employees.windowDays").value(Integer.parseInt(newValue)));

		as(adminToken, put("/api/admin/settings/" + WINDOW), "{\"version\":%d,\"value\":\"30\"}".formatted(version))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(adminToken, put("/api/admin/settings/" + WINDOW), "{\"version\":%d,\"value\":\"500\"}".formatted(version + 1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SETTING"));
		as(adminToken, put("/api/admin/settings/made.up"), "{\"version\":0,\"value\":\"1\"}")
			.andExpect(status().isNotFound());
		get(employeeToken, "/api/admin/settings").andExpect(status().isForbidden());

		get(adminToken, "/api/admin/audit-logs", "action", "SETTING_UPDATED", "actorId", adminId.toString())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].module").value("SETTINGS"))
			.andExpect(jsonPath("$.content[0].actor.id").value(adminId))
			.andExpect(jsonPath("$.content[0].details.changes['workload.windowDays'].from").value(originalWindow))
			.andExpect(jsonPath("$.content[0].details.changes['workload.windowDays'].to").value(newValue));
		// Free text searches inside the details, case-insensitively.
		get(adminToken, "/api/admin/audit-logs", "search", "WINDOWDAYS", "actorId", adminId.toString())
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	@SuppressWarnings("unchecked")
	void thePermissionMatrixChangesARoleWithinTheRules() throws Exception {
		String roles = get(adminToken, "/api/roles").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		int version = ((List<Integer>) JsonPath.read(roles, "$[?(@.code == 'EMPLOYEE')].version")).get(0);
		TreeSet<String> permissions = new TreeSet<>(
				(List<String>) ((List<List<String>>) JsonPath.read(roles, "$[?(@.code == 'EMPLOYEE')].permissions")).get(0));
		assertThat(permissions).doesNotContain("WORKLOAD_VIEW");
		get(employeeToken, "/api/workload").andExpect(status().isForbidden());

		TreeSet<String> withWorkload = new TreeSet<>(permissions);
		withWorkload.add("WORKLOAD_VIEW");
		as(accessAdminToken, put("/api/roles/EMPLOYEE/permissions"), body(version, withWorkload))
			.andExpect(status().isForbidden());
		as(adminToken, put("/api/roles/EMPLOYEE/permissions"), body(version, withWorkload)).andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions").value(hasItem("WORKLOAD_VIEW")))
			.andExpect(jsonPath("$.version").value(version + 1));
		// Authorities are read from the database on every request: the employee has it at once.
		get(employeeToken, "/api/auth/me").andExpect(jsonPath("$.permissions").value(hasItem("WORKLOAD_VIEW")));
		get(employeeToken, "/api/workload").andExpect(status().isOk());

		as(adminToken, put("/api/roles/EMPLOYEE/permissions"), body(version, permissions))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		TreeSet<String> marketing = new TreeSet<>(withWorkload);
		marketing.add("MARKETING_VIEW");
		as(adminToken, put("/api/roles/EMPLOYEE/permissions"), body(version + 1, marketing))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MARKETING_PERMISSION_ON_ROLE"));
		TreeSet<String> editWithoutView = new TreeSet<>(withWorkload);
		editWithoutView.remove("TASK_VIEW");
		as(adminToken, put("/api/roles/EMPLOYEE/permissions"), body(version + 1, editWithoutView))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VIEW_PERMISSION_REQUIRED"));
		as(adminToken, put("/api/roles/SUPER_ADMIN/permissions"), body(0, permissions))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ROLE_LOCKED"));

		get(adminToken, "/api/admin/audit-logs", "event", "USER_PERMISSION_CHANGE", "actorId", adminId.toString())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].action").value("ROLE_PERMISSIONS_CHANGED"))
			.andExpect(jsonPath("$.content[0].entityType").value("ROLE"))
			.andExpect(jsonPath("$.content[0].details.role").value("EMPLOYEE"))
			.andExpect(jsonPath("$.content[0].details.added[0]").value("WORKLOAD_VIEW"));
	}

	@Test
	void approvalTypesCanBeAddedChangedAndRetired() throws Exception {
		String code = "IT_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String created = as(adminToken, post("/api/approvals/types"), """
				{"code":"%s","name":"Travel request","description":"Trips and conferences","requiresAmount":true,
				 "steps":[{"approverKind":"DEPARTMENT_MANAGER"},{"approverKind":"ROLE","roleCode":"SUPER_ADMIN"}]}
				""".formatted(code))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.steps.length()").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(created, "$.id")).longValue();
		approvalTypes.add(id);
		int version = JsonPath.read(created, "$.version");

		as(adminToken, post("/api/approvals/types"), """
				{"code":"%s","name":"Again","steps":[{"approverKind":"DEPARTMENT_MANAGER"}]}
				""".formatted(code))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("APPROVAL_TYPE_CODE_TAKEN"));
		as(employeeToken, put("/api/approvals/types/" + id),
				"{\"version\":%d,\"name\":\"Travel\",\"requiresAmount\":true,\"active\":false}".formatted(version))
			.andExpect(status().isForbidden());

		as(adminToken, put("/api/approvals/types/" + id),
				"{\"version\":%d,\"name\":\"Travel request\",\"description\":\"Trips and conferences\",\"requiresAmount\":true,\"active\":false}"
					.formatted(version))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false))
			.andExpect(jsonPath("$.version").value(version + 1));
		as(adminToken, put("/api/approvals/types/" + id),
				"{\"version\":%d,\"name\":\"Travel\",\"requiresAmount\":true,\"active\":true}".formatted(version))
			.andExpect(status().isConflict());

		get(adminToken, "/api/admin/audit-logs", "module", "APPROVALS", "entityType", "APPROVAL_TYPE", "entityId",
				id.toString())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].action").value("APPROVAL_TYPE_UPDATED"))
			.andExpect(jsonPath("$.content[0].details.changes.active.to").value(false))
			.andExpect(jsonPath("$.content[1].action").value("APPROVAL_TYPE_CREATED"));
	}

	@Test
	void theAuditLogFiltersEveryBriefEventAndExports() throws Exception {
		String today = calendar.today().toString();
		String tomorrow = calendar.today().plusDays(1).toString();
		String employee = employeeId.toString();

		get(adminToken, "/api/admin/audit-logs", "event", "USER_LOGIN", "actorId", employee).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].action").value("LOGIN"))
			.andExpect(jsonPath("$.content[0].actionLabel").value("Signed in"))
			.andExpect(jsonPath("$.content[0].module").value("SIGN_IN"))
			.andExpect(jsonPath("$.content[0].actor.id").value(employeeId));
		get(adminToken, "/api/admin/audit-logs", "actorId", employee, "from", today, "to", today)
			.andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
		get(adminToken, "/api/admin/audit-logs", "actorId", employee, "from", tomorrow)
			.andExpect(jsonPath("$.totalElements").value(0));
		get(adminToken, "/api/admin/audit-logs", "actorId", employee, "module", "TASKS")
			.andExpect(jsonPath("$.totalElements").value(0));
		// Free text also finds entries by the actor's name or email.
		get(adminToken, "/api/admin/audit-logs", "event", "USER_LOGIN", "search", users.email(employeeId).substring(0, 15))
			.andExpect(jsonPath("$.totalElements").value(1));
		get(adminToken, "/api/admin/audit-logs", "from", tomorrow, "to", today).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RANGE"));
		get(employeeToken, "/api/admin/audit-logs").andExpect(status().isForbidden());

		get(adminToken, "/api/admin/audit-logs/catalog").andExpect(status().isOk())
			.andExpect(jsonPath("$.events.length()").value(13))
			.andExpect(jsonPath("$.events[0].label").value("User login"))
			.andExpect(jsonPath("$.actions[?(@.action == 'SETTING_UPDATED')].module").value(hasItem("SETTINGS")))
			.andExpect(jsonPath("$.actors[*].id").value(hasItem(employeeId.intValue())))
			.andExpect(jsonPath("$.entityTypes").isArray());

		get(adminToken, "/api/admin/audit-logs/export", "actorId", employee).andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/csv"))
			.andExpect(content().string(containsString("Time,Action,Module,Actor,Actor email")))
			.andExpect(content().string(containsString(users.email(employeeId))));
		get(adminToken, "/api/admin/audit-logs/export", "format", "PDF").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FORMAT_NOT_AVAILABLE"));
	}

	private static String body(int version, TreeSet<String> permissions) {
		return "{\"version\":%d,\"permissions\":[%s]}".formatted(version,
				String.join(",", permissions.stream().map(p -> "\"" + p + "\"").toList()));
	}

	private ResultActions get(String token, String path, String... params) throws Exception {
		var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
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
