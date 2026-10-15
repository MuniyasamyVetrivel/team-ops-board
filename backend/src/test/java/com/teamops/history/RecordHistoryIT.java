package com.teamops.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.tag.Tag;
import com.teamops.tag.TagRepository;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase X1 against MySQL: every create, edit and delete is logged with a field diff and stamped with who and when,
 * a record's History tab includes its children and is scoped like the record, secrets are never logged, finance
 * amounts are hidden from non-finance viewers, and rolled-back changes leave no entry. Runs in a throwaway
 * department. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads" })
class RecordHistoryIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private final List<Long> tasks = new ArrayList<>();

	private final List<Long> approvals = new ArrayList<>();

	private Long departmentId;

	private Long managerId;

	private Long employeeId;

	private String adminToken;

	private String managerToken;

	private String employeeToken;

	private String outsiderToken;

	private String auditorToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		String code = "HIS_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"History %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();
		managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		employeeId = users.create(departmentId, RoleCodes.EMPLOYEE);
		Long outsiderId = users.create("IT", RoleCodes.EMPLOYEE);
		Long auditorId = users.create("IT", RoleCodes.EMPLOYEE, "AUDIT_VIEW");
		managerToken = login(users.email(managerId));
		employeeToken = login(users.email(employeeId));
		outsiderToken = login(users.email(outsiderId));
		auditorToken = login(users.email(auditorId));
	}

	@AfterEach
	void cleanUp() {
		tasks.forEach(taskRepository::deleteById);
		approvals.forEach(id -> {
			jdbc.update("DELETE FROM approval_steps WHERE approval_id = ?", id);
			jdbc.update("DELETE FROM approvals WHERE id = ?", id);
		});
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void aTasksHistoryHoldsEveryChangeAndItsChildrenWithWhoAndWhen() throws Exception {
		String created = as(managerToken, post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
				{"title":"Audit the vendor list","departmentId":%d,"assigneeId":%d,"priority":"HIGH",
				 "estimatedHours":6,"tags":["audit-x1"]}
				""".formatted(departmentId, employeeId)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long taskId = ((Number) JsonPath.read(created, "$.id")).longValue();
		tasks.add(taskId);
		String taskCode = JsonPath.read(created, "$.code");
		int version = JsonPath.read(created, "$.version");

		// Stamps: who created and last changed the row.
		assertThat(jdbc.queryForMap("SELECT created_by, updated_by FROM tasks WHERE id = ?", taskId))
			.containsEntry("created_by", managerId)
			.containsEntry("updated_by", managerId);

		as(employeeToken, post("/api/tasks/%d/comments".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"On it\"}")).andExpect(status().isOk());
		as(managerToken, put("/api/tasks/" + taskId).contentType(MediaType.APPLICATION_JSON).content("""
				{"version":%d,"title":"Audit the supplier list","departmentId":%d,"priority":"HIGH",
				 "estimatedHours":6,"tags":["audit-x1"]}
				""".formatted(version, departmentId))).andExpect(status().isOk());

		List<Map<String, Object>> history = history(employeeToken, "TASK", taskId);
		Map<String, Object> creation = only(history, "RECORD_CREATED", "TASK");
		assertThat(creation.get("entityLabel")).isEqualTo(taskCode);
		assertThat(creation.get("actionLabel")).isEqualTo("Task created");
		assertThat(creation.get("module")).isEqualTo("TASKS");
		assertThat(actorId(creation)).isEqualTo(managerId);
		Map<String, Map<String, Object>> createdFields = changes(creation);
		assertThat(createdFields.get("title")).containsEntry("to", "Audit the vendor list");
		assertThat(createdFields.get("assignee")).containsEntry("ref", "USER").containsEntry("toLabel", "Integration EMPLOYEE");
		assertThat(createdFields.get("tags")).containsEntry("ref", "TAG");
		assertThat(createdFields).doesNotContainKeys("version", "createdAt", "updatedAt", "createdById");

		Map<String, Object> edit = only(history, "RECORD_UPDATED", "TASK");
		assertThat(changes(edit)).containsOnlyKeys("title");
		assertThat(changes(edit).get("title")).containsEntry("from", "Audit the vendor list")
			.containsEntry("to", "Audit the supplier list");
		assertThat(edit.get("createdAt")).isNotNull();
		assertThat(edit.get("ipAddress")).as("only audit viewers see IP addresses").isNull();

		Map<String, Object> comment = only(history, "RECORD_CREATED", "TASK_COMMENT");
		assertThat(comment).containsEntry("parentType", "TASK");
		assertThat(((Number) comment.get("parentId")).longValue()).isEqualTo(taskId);
		assertThat(actorId(comment)).isEqualTo(employeeId);
		assertThat(changes(comment).get("body")).containsEntry("to", "On it");

		// The module's own business event sits in the same timeline.
		assertThat(history).anyMatch(e -> "TASK_CREATED".equals(e.get("action")));

		// Scoped like the record itself; audit viewers see everything, with the request origin.
		as(outsiderToken, get("/api/history/TASK/" + taskId)).andExpect(status().isNotFound());
		List<Map<String, Object>> audited = history(auditorToken, "task", taskId);
		assertThat(only(audited, "RECORD_UPDATED", "TASK").get("ipAddress")).isNotNull();

		// The audit log filters by module, record and children, and exports to CSV.
		String page = as(auditorToken, get("/api/admin/audit-logs").param("module", "TASKS")
			.param("entityType", "TASK")
			.param("entityId", taskId.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> actions = JsonPath.read(page, "$.content[*].action");
		assertThat(actions).contains("RECORD_CREATED", "RECORD_UPDATED", "TASK_CREATED");
		List<String> types = JsonPath.read(page, "$.content[*].entityType");
		assertThat(types).contains("TASK_COMMENT");
		String csv = as(auditorToken, get("/api/admin/audit-logs/export").param("entityType", "TASK")
			.param("entityId", taskId.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(csv).contains("Task edited").contains(taskCode);

		// A delete straight through the repository (outside the recycle bin) is a permanent delete: logged with the
		// record's last values, and the history stays.
		as(managerToken, post("/api/tasks/%d/watchers".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":%d}".formatted(managerId))).andExpect(status().isOk());
		// Many-to-many links (watchers, tags) are logged on their owner as before → after id lists.
		assertThat(history(auditorToken, "TASK", taskId)).anyMatch(e -> "RECORD_UPDATED".equals(e.get("action"))
				&& changes(e).containsKey("watchers")
				&& List.of(managerId.intValue()).equals(changes(e).get("watchers").get("to"))
				&& List.of("Integration DEPARTMENT_MANAGER").equals(changes(e).get("watchers").get("toLabel")));
		transactions.executeWithoutResult(status -> taskRepository.deleteById(taskId));
		tasks.remove(taskId);
		Map<String, Object> deletion = only(history(auditorToken, "TASK", taskId), "RECORD_PURGED", "TASK");
		assertThat(changes(deletion).get("title")).containsEntry("from", "Audit the supplier list");
		assertThat(deletion.get("actor")).as("no signed-in user: the system").isNull();
	}

	@Test
	void secretsAreNeverLogged() throws Exception {
		as(employeeToken, post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"%s\",\"newPassword\":\"Changed12345\"}".formatted(IntegrationUsers.PASSWORD)))
			.andExpect(status().isOk());
		String details = jdbc.queryForObject("""
				SELECT details FROM audit_logs WHERE action = 'RECORD_UPDATED' AND entity_type = 'USER' AND entity_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, employeeId);
		assertThat(details).contains("\"passwordHash\": {\"redacted\": true}").doesNotContain("$2");
		String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, employeeId);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE details LIKE ?", Long.class,
				"%" + hash.substring(7) + "%")).isZero();
	}

	@Test
	void financeAmountsAreOnlyShownToFinanceViewers() throws Exception {
		Long typeId = jdbc.queryForObject("SELECT id FROM approval_types WHERE active ORDER BY id LIMIT 1", Long.class);
		String submitted = as(employeeToken, post("/api/approvals").contentType(MediaType.APPLICATION_JSON)
			.content("{\"typeId\":%d,\"title\":\"Licence renewal\",\"amount\":42137.50,\"currency\":\"INR\"}"
				.formatted(typeId)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long approvalId = ((Number) JsonPath.read(submitted, "$.id")).longValue();
		approvals.add(approvalId);

		List<Map<String, Object>> asRequester = history(employeeToken, "APPROVAL", approvalId);
		assertThat(changes(only(asRequester, "RECORD_CREATED", "APPROVAL")).get("amount"))
			.isEqualTo(Map.of("hidden", true, "finance", true));
		@SuppressWarnings("unchecked")
		Map<String, Object> event = (Map<String, Object>) only(asRequester, "APPROVAL_SUBMITTED", "APPROVAL")
			.get("details");
		assertThat(event).containsEntry("amount", null).containsEntry("hiddenFields", List.of("amount"));
		assertThat(asRequester).anyMatch(e -> "APPROVAL_STEP".equals(e.get("entityType")));

		List<Map<String, Object>> asAdmin = history(adminToken, "APPROVAL", approvalId);
		assertThat(changes(only(asAdmin, "RECORD_CREATED", "APPROVAL")).get("amount")).containsEntry("to", 42137.5)
			.containsEntry("finance", true);

		// An audit viewer without FINANCE_VIEW cannot find the amount by searching for it either.
		String found = as(auditorToken, get("/api/admin/audit-logs").param("search", "42137"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat((Integer) JsonPath.read(found, "$.totalElements")).isZero();
		String foundByAdmin = as(adminToken, get("/api/admin/audit-logs").param("search", "42137"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat((Integer) JsonPath.read(foundByAdmin, "$.totalElements")).isPositive();
	}

	@Test
	void aRolledBackChangeLeavesNoEntryAndSystemChangesHaveNoUser() {
		String rolledBack = "x1-rollback-" + System.nanoTime();
		transactions.executeWithoutResult(status -> {
			Tag tag = new Tag();
			tag.setName(rolledBack);
			tagRepository.saveAndFlush(tag);
			status.setRollbackOnly();
		});
		assertThat(countEntries("TAG", rolledBack)).isZero();

		String kept = "x1-kept-" + System.nanoTime();
		Long tagId = transactions.execute(status -> {
			Tag tag = new Tag();
			tag.setName(kept);
			return tagRepository.save(tag).getId();
		});
		assertThat(countEntries("TAG", kept)).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT created_by, updated_by, created_at, updated_at FROM tags WHERE id = ?",
				tagId))
			.containsEntry("created_by", null)
			.containsEntry("updated_by", null)
			.doesNotContainEntry("created_at", null);
		assertThat(jdbc.queryForObject("SELECT actor_id FROM audit_logs WHERE entity_type = 'TAG' AND entity_id = ?",
				Long.class, tagId)).isNull();

		transactions.executeWithoutResult(status -> tagRepository.deleteById(tagId));
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'TAG' AND entity_id = ? AND action = 'RECORD_DELETED'",
				Long.class, tagId)).isEqualTo(1);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private long countEntries(String type, String label) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = ? AND entity_label = ?",
				Long.class, type, label);
	}

	private List<Map<String, Object>> history(String token, String type, Long id) throws Exception {
		String body = as(token, get("/api/history/%s/%d".formatted(type, id)).param("size", "100"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.content");
	}

	private static Map<String, Object> only(List<Map<String, Object>> entries, String action, String type) {
		List<Map<String, Object>> matching = entries.stream()
			.filter(e -> action.equals(e.get("action")) && type.equals(e.get("entityType")))
			.toList();
		assertThat(matching).as(action + " " + type).hasSize(1);
		return matching.get(0);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Object>> changes(Map<String, Object> entry) {
		return (Map<String, Map<String, Object>>) ((Map<String, Object>) entry.get("details")).get("changes");
	}

	@SuppressWarnings("unchecked")
	private static Long actorId(Map<String, Object> entry) {
		return ((Number) ((Map<String, Object>) entry.get("actor")).get("id")).longValue();
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
