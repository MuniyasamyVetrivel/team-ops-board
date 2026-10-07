package com.teamops.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
import com.teamops.support.IntegrationUsers;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 5 end-to-end against MySQL: task lifecycle, scope, collaboration, attachments and workload. Runs in a
 * throwaway department so counts are not affected by other data. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads" })
class TaskFlowIT {

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
	private JdbcTemplate jdbc;

	@Autowired
	private BusinessCalendar calendar;

	private IntegrationUsers users;

	private final List<Long> tasks = new ArrayList<>();

	private Long departmentId;

	private Long managerId;

	private Long employeeId;

	private Long outsiderId;

	private String managerToken;

	private String employeeToken;

	private String outsiderToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		String adminToken = login(users.email(adminId));
		String code = "TSK_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Tasks %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();

		managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		employeeId = users.create(departmentId, RoleCodes.EMPLOYEE);
		outsiderId = users.create("IT", RoleCodes.EMPLOYEE);
		managerToken = login(users.email(managerId));
		employeeToken = login(users.email(employeeId));
		outsiderToken = login(users.email(outsiderId));
	}

	@AfterEach
	void cleanUp() {
		tasks.forEach(taskRepository::deleteById);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void taskLifecycleWithScopeHistoryAndWorkload() throws Exception {
		LocalDate today = calendar.today();

		// Manager creates and assigns; the code comes from the sequence.
		Long taskId = create(managerToken, """
				{"title":"Prepare Q4 plan","departmentId":%d,"assigneeId":%d,"priority":"HIGH",
				 "dueDate":"%s","estimatedHours":16,"tags":["Planning","q4"]}
				""".formatted(departmentId, employeeId, today.minusDays(1)));
		as(employeeToken, get("/api/tasks/" + taskId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(matchesPattern("TSK-\\d{6}")))
			.andExpect(jsonPath("$.dueState").value("OVERDUE"))
			.andExpect(jsonPath("$.tags[0]").value("planning"))
			.andExpect(jsonPath("$.permissions.canEdit").value(true))
			.andExpect(jsonPath("$.permissions.canCancel").value(false));

		// An employee from another department cannot see it at all.
		as(outsiderToken, get("/api/tasks/" + taskId)).andExpect(status().isNotFound());
		as(outsiderToken, get("/api/tasks").param("search", "Prepare Q4 plan"))
			.andExpect(jsonPath("$.totalElements").value(0));

		// My Tasks: overdue view and summary for the assignee.
		as(employeeToken, get("/api/tasks").param("view", "ASSIGNED_TO_ME").param("due", "OVERDUE"))
			.andExpect(jsonPath("$.totalElements").value(1));
		as(employeeToken, get("/api/tasks/my/summary")).andExpect(jsonPath("$.overdue").value(1))
			.andExpect(jsonPath("$.active").value(1));

		// Workload: 16 h remaining of 80 h capacity = 20% (LOW), visible to the manager.
		as(managerToken, get("/api/workload").param("departmentId", departmentId.toString()))
			.andExpect(jsonPath("$.rows[?(@.user.id == %d)].workloadPercent".formatted(employeeId)).value(20))
			.andExpect(jsonPath("$.rows[?(@.user.id == %d)].overdue".formatted(employeeId)).value(1))
			.andExpect(jsonPath("$.rows[?(@.user.id == %d)].level".formatted(employeeId)).value("LOW"));
		// Employees lack WORKLOAD_VIEW but may read their own row.
		as(employeeToken, get("/api/workload")).andExpect(status().isForbidden());
		as(employeeToken, get("/api/workload").param("userId", employeeId.toString()))
			.andExpect(jsonPath("$.rows.length()").value(1));

		// Collaboration: comment, checklist, attachment.
		as(employeeToken, post("/api/tasks/%d/comments".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"Draft attached\"}")).andExpect(jsonPath("$.comments[0].body").value("Draft attached"));
		as(employeeToken, post("/api/tasks/%d/checklist".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"Collect inputs\"}")).andExpect(jsonPath("$.checklist[0].done").value(false));
		MockMultipartFile file = new MockMultipartFile("file", "plan.txt", "text/html",
				"Q4 plan".getBytes(StandardCharsets.UTF_8));
		String withFile = as(employeeToken, multipart("/api/tasks/%d/attachments".formatted(taskId)).file(file))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attachments[0].contentType").value("text/plain"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long fileId = ((Number) JsonPath.read(withFile, "$.attachments[0].fileId")).longValue();
		as(employeeToken, get("/api/tasks/%d/attachments/%d".formatted(taskId, fileId)))
			.andExpect(status().isOk())
			.andExpect(content().string("Q4 plan"));
		as(outsiderToken, get("/api/tasks/%d/attachments/%d".formatted(taskId, fileId)))
			.andExpect(status().isNotFound());
		as(employeeToken, multipart("/api/tasks/%d/attachments".formatted(taskId))
			.file(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[] { 1 })))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));

		// Optimistic locking: an edit based on an old version is rejected.
		int version = JsonPath.read(as(employeeToken, get("/api/tasks/" + taskId)).andReturn()
			.getResponse()
			.getContentAsString(), "$.version");
		String update = """
				{"version":%d,"title":"Prepare Q4 plan","departmentId":%d,"priority":"URGENT","dueDate":"%s",
				 "estimatedHours":16,"actualHours":4,"tags":["planning"]}
				""";
		as(employeeToken, put("/api/tasks/" + taskId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(version, departmentId, today.plusDays(3))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.priority").value("URGENT"))
			.andExpect(jsonPath("$.dueState").value("DUE_SOON"));
		as(employeeToken, put("/api/tasks/" + taskId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(version, departmentId, today.plusDays(4))))
			.andExpect(status().isConflict());

		// Complete then reopen; history and audit record both.
		as(employeeToken, put("/api/tasks/%d/status".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"COMPLETED\"}")).andExpect(jsonPath("$.completedAt").exists());
		as(employeeToken, get("/api/tasks/my/summary")).andExpect(jsonPath("$.completedThisWeek").value(1));
		as(employeeToken, put("/api/tasks/%d/status".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"IN_PROGRESS\"}"))
			.andExpect(jsonPath("$.completedAt").doesNotExist())
			.andExpect(jsonPath("$.history[*].field", hasItem("reopened")));
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'TASK_STATUS_CHANGED' and entity_id = ?", Integer.class,
				taskId)).isEqualTo(2);

		// Employees cannot cancel (no TASK_DELETE); the manager can.
		as(employeeToken, put("/api/tasks/%d/status".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"CANCELLED\"}")).andExpect(status().isForbidden());
		as(managerToken, put("/api/tasks/%d/status".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"CANCELLED\"}")).andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void assignmentScopeDependenciesAndWatchers() throws Exception {
		Long first = create(managerToken, "{\"title\":\"First\",\"departmentId\":%d}".formatted(departmentId));
		Long second = create(managerToken, "{\"title\":\"Second\",\"departmentId\":%d}".formatted(departmentId));

		// Managers cannot assign people outside their departments.
		as(managerToken, put("/api/tasks/%d/assignee".formatted(first)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"assigneeId\":%d}".formatted(outsiderId)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CANNOT_ASSIGN"));
		as(managerToken, put("/api/tasks/%d/assignee".formatted(first)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"assigneeId\":%d}".formatted(employeeId))).andExpect(status().isOk());

		// Dependencies reject self-references and cycles.
		as(managerToken, post("/api/tasks/%d/dependencies".formatted(second)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"dependsOnTaskId\":%d}".formatted(first))).andExpect(jsonPath("$.dependencies[0].id").value(first));
		as(managerToken, post("/api/tasks/%d/dependencies".formatted(first)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"dependsOnTaskId\":%d}".formatted(second)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEPENDENCY_CYCLE"));
		as(managerToken, post("/api/tasks/%d/dependencies".formatted(first)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"dependsOnTaskId\":%d}".formatted(first)))
			.andExpect(jsonPath("$.code").value("INVALID_DEPENDENCY"));

		// Watching gives an outsider read access, but not edit access.
		as(managerToken, post("/api/tasks/%d/watchers".formatted(second)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":%d}".formatted(outsiderId))).andExpect(status().isOk());
		as(outsiderToken, get("/api/tasks/" + second)).andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions.canEdit").value(false));
		as(outsiderToken, get("/api/tasks").param("view", "WATCHING")).andExpect(jsonPath("$.totalElements").value(1));
		as(outsiderToken, put("/api/tasks/%d/status".formatted(second)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isForbidden());
		as(outsiderToken, delete("/api/tasks/%d/watchers/%d".formatted(second, outsiderId)))
			.andExpect(status().isNoContent());
		as(outsiderToken, get("/api/tasks/" + second)).andExpect(status().isNotFound());

		// Unassigned tasks in the department are visible to its manager, not to the employee.
		as(managerToken, get("/api/tasks").param("departmentId", departmentId.toString()))
			.andExpect(jsonPath("$.totalElements").value(2));
		as(employeeToken, get("/api/tasks").param("departmentId", departmentId.toString()))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[*].title", not(hasItem("Second"))));
	}

	@Test
	void tasksWithoutDueDatesSortLast() throws Exception {
		LocalDate today = calendar.today();
		create(managerToken, "{\"title\":\"Undated\",\"departmentId\":%d}".formatted(departmentId));
		create(managerToken, "{\"title\":\"Dated\",\"departmentId\":%d,\"dueDate\":\"%s\"}".formatted(departmentId,
				today.plusDays(2)));

		as(managerToken, get("/api/tasks").param("departmentId", departmentId.toString()).param("sort", "due,asc"))
			.andExpect(jsonPath("$.content[0].title").value("Dated"))
			.andExpect(jsonPath("$.content[1].title").value("Undated"));
	}

	private Long create(String token, String body) throws Exception {
		String response = as(token, post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(response, "$.id")).longValue();
		tasks.add(id);
		return id;
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
