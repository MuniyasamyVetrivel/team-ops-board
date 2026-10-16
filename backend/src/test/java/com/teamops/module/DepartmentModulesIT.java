package com.teamops.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.module.service.ModuleDefinition;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * The department modules framework end to end against MySQL, with a throwaway module ("it-probe", its own two
 * permissions) in two throwaway departments: the Super Admin switches it on per department, the sidebar and the
 * module's endpoints follow the switch and the permissions, and every change is audited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads" })
class DepartmentModulesIT {

	private static final String KEY = "it-probe";

	private static final String VIEW = "IT_PROBE_VIEW";

	private static final String EDIT = "IT_PROBE_EDIT";

	@TestConfiguration
	static class ProbeModule {

		@Bean
		ModuleDefinition itProbeModule() {
			return new ModuleDefinition(KEY, "IT probe", "Wrench", "Integration test module", VIEW, EDIT);
		}

		@Bean
		ProbeController itProbeController() {
			return new ProbeController();
		}

	}

	/** One read and one write endpoint, deliberately without {@code @PreAuthorize}. */
	@RestController
	@RequestMapping("/api/modules/it-probe/items")
	static class ProbeController {

		@GetMapping
		List<String> list() {
			return List.of("item");
		}

		@PostMapping
		List<String> create() {
			return List.of("created");
		}

	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private ModuleAccessService moduleAccess;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private Long departmentA;

	private Long departmentB;

	private Long viewerId;

	private Long memberId;

	private Long editorId;

	private Long plainId;

	private String adminToken;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("insert into permissions (code, name, module) values (?, 'IT probe view', 'ADMIN'), "
				+ "(?, 'IT probe edit', 'ADMIN')", VIEW, EDIT);
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(adminId);
		departmentA = createDepartment("MODA");
		departmentB = createDepartment("MODB");

		viewerId = users.create(departmentA, RoleCodes.EMPLOYEE, VIEW);
		plainId = users.create(departmentA, RoleCodes.EMPLOYEE);
		editorId = users.create(departmentB, RoleCodes.EMPLOYEE, VIEW, EDIT);
		memberId = users.create("IT", RoleCodes.EMPLOYEE, VIEW);
		as(adminToken, put("/api/departments/%d/members/%d".formatted(departmentA, memberId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"MEMBER\"}")).andExpect(status().is2xxSuccessful());
	}

	@AfterEach
	void cleanUp() {
		users.deleteAll();
		departmentRepository.deleteById(departmentA);
		departmentRepository.deleteById(departmentB);
		jdbc.update("delete from department_modules where module_key = ?", KEY);
		jdbc.update("delete from permissions where code in (?, ?)", VIEW, EDIT);
	}

	@Test
	void modulesFollowTheDepartmentSwitchAndThePermissions() throws Exception {
		String viewer = login(viewerId);
		String member = login(memberId);
		String editor = login(editorId);
		String plain = login(plainId);

		// Off everywhere: nobody can use it, the Super Admin included.
		as(adminToken, get("/api/admin/modules")).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.key == 'it-probe')].departments[*]").isEmpty());
		as(viewer, get("/api/department-tools")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].key", not(hasItem(KEY))));
		as(viewer, get("/api/modules/it-probe/items")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		as(adminToken, get("/api/modules/it-probe/items")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));

		// Only MODULE_MANAGE holders switch modules.
		as(viewer, get("/api/admin/modules")).andExpect(status().isForbidden());
		as(viewer, switchFor(departmentA, true)).andExpect(status().isForbidden());

		// On for department A: its permitted people (primary or member) may read; editing needs the edit permission.
		as(adminToken, switchFor(departmentA, true)).andExpect(status().isOk())
			.andExpect(jsonPath("$.departments[0].id").value(departmentA));
		as(adminToken, switchFor(departmentA, true)).andExpect(status().isOk());
		assertThat(auditCount("MODULE_ENABLED")).as("a switch already on records nothing").isEqualTo(1);

		as(viewer, get("/api/department-tools")).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.key == 'it-probe')].canEdit").value(false));
		as(viewer, get("/api/modules/it-probe/items")).andExpect(status().isOk());
		as(viewer, post("/api/modules/it-probe/items")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		as(member, get("/api/modules/it-probe/items")).andExpect(status().isOk());
		as(plain, get("/api/modules/it-probe/items")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		as(plain, get("/api/department-tools")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].key", not(hasItem(KEY))));
		as(editor, post("/api/modules/it-probe/items")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		as(adminToken, post("/api/modules/it-probe/items")).andExpect(status().isOk());
		as(adminToken, get("/api/department-tools")).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.key == 'it-probe')].canEdit").value(true));

		// On for department B as well: its editor may change things.
		as(adminToken, switchFor(departmentB, true)).andExpect(status().isOk());
		as(editor, post("/api/modules/it-probe/items")).andExpect(status().isOk());
		assertThat(moduleAccess.recipients(KEY).stream().map(User::getId).toList())
			.contains(viewerId, memberId, editorId)
			.doesNotContain(plainId);

		// Off for department A again: its people lose access, the row stays as history.
		as(adminToken, switchFor(departmentA, false)).andExpect(status().isOk());
		as(viewer, get("/api/modules/it-probe/items")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		assertThat(auditCount("MODULE_DISABLED")).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"select count(*) from department_modules where module_key = ? and department_id = ? and enabled = false",
				Integer.class, KEY, departmentA)).isEqualTo(1);

		// An inactive department does not count, and cannot be switched on.
		departmentRepository.findById(departmentB).ifPresent(department -> {
			department.setStatus(DepartmentStatus.INACTIVE);
			departmentRepository.save(department);
		});
		as(editor, get("/api/modules/it-probe/items")).andExpect(status().isForbidden());
		as(adminToken, switchFor(departmentA, true)).andExpect(status().isOk());
		jdbc.update("update department_modules set enabled = false where module_key = ? and department_id = ?", KEY,
				departmentB);
		as(adminToken, switchFor(departmentB, true)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEPARTMENT_INACTIVE"));

		as(adminToken, put("/api/admin/modules/no-such-tool/departments/" + departmentA)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":true}")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
	}

	private MockHttpServletRequestBuilder switchFor(Long departmentId, boolean enabled) {
		return put("/api/admin/modules/%s/departments/%d".formatted(KEY, departmentId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":%s}".formatted(enabled));
	}

	/** Audit entries of this test's department A (audit logs outlive the test). */
	private int auditCount(String action) {
		return jdbc.queryForObject(
				"select count(*) from audit_logs where action = ? and entity_type = 'DEPARTMENT_MODULE' "
						+ "and json_unquote(json_extract(details, '$.module')) = ? "
						+ "and json_extract(details, '$.departmentId') = ?",
				Integer.class, action, KEY, departmentA);
	}

	private Long createDepartment(String prefix) throws Exception {
		String code = prefix + "_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Modules %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String login(Long userId) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(users.email(userId), IntegrationUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

}
