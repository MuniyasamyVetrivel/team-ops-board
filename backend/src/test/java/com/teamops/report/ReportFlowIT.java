package com.teamops.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
import com.teamops.project.repository.ProjectRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 20 end-to-end against MySQL: the task, workload, ticket and project reports in a throwaway department (so
 * seeded data never changes the counts), their filters and permissions, and the CSV export through the report
 * exporter. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class ReportFlowIT {

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
	private ProjectRepository projectRepository;

	@Autowired
	private BusinessCalendar calendar;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private final List<Long> tasks = new ArrayList<>();

	private final List<Long> projects = new ArrayList<>();

	private Long departmentId;

	private Long employeeId;

	private String managerToken;

	private String employeeToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		String adminToken = login(users.email(adminId));
		String code = "RPT_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String body = as(adminToken, post("/api/departments"), "{\"name\":\"Reports %s\",\"code\":\"%s\"}".formatted(code, code))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();
		Long managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		employeeId = users.create(departmentId, RoleCodes.EMPLOYEE);
		managerToken = login(users.email(managerId));
		employeeToken = login(users.email(employeeId));
	}

	@AfterEach
	void cleanUp() {
		tasks.forEach(taskRepository::deleteById);
		projects.forEach(projectRepository::deleteById);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void theTaskReportCountsCompletionOverdueAndProductivity() throws Exception {
		LocalDate today = calendar.today();
		Long onTime = task("Done on time", today.plusDays(2), 3);
		task("Overdue", today.minusDays(1), 0);
		task("Undated", null, 0);
		as(employeeToken, put("/api/tasks/%d/status".formatted(onTime)), "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

		get(managerToken, "/api/reports/tasks", "departmentId", departmentId.toString()).andExpect(status().isOk())
			.andExpect(jsonPath("$.range.to").value(today.toString()))
			.andExpect(jsonPath("$.range.from").value(today.minusDays(29).toString()))
			.andExpect(jsonPath("$.summary.created").value(3))
			.andExpect(jsonPath("$.summary.completed").value(1))
			.andExpect(jsonPath("$.summary.completedOnTime").value(1))
			.andExpect(jsonPath("$.summary.onTimePct").value(100))
			.andExpect(jsonPath("$.summary.open").value(2))
			.andExpect(jsonPath("$.summary.overdue").value(1))
			.andExpect(jsonPath("$.summary.overduePct").value(50))
			.andExpect(jsonPath("$.summary.hoursLogged").value(3.0))
			.andExpect(jsonPath("$.openByStatus[?(@.status == 'TODO')].tasks").value(Matchers.contains(2)))
			.andExpect(jsonPath("$.departments.length()").value(1))
			.andExpect(jsonPath("$.departments[0].department.id").value(departmentId))
			.andExpect(jsonPath("$.employees[0].user.id").value(employeeId))
			.andExpect(jsonPath("$.employees[0].completed").value(1))
			.andExpect(jsonPath("$.trend[-1].created").value(3))
			.andExpect(jsonPath("$.trend[-1].completed").value(1));

		// The status filter narrows every count to tasks in those statuses now.
		get(managerToken, "/api/reports/tasks", "departmentId", departmentId.toString(), "status", "TODO")
			.andExpect(jsonPath("$.summary.created").value(2))
			.andExpect(jsonPath("$.summary.completed").value(0));
		get(managerToken, "/api/reports/tasks", "from", today.toString(), "to", today.minusDays(1).toString())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RANGE"));

		// Employees have no reports.
		get(employeeToken, "/api/reports/tasks").andExpect(status().isForbidden());

		String csv = get(managerToken, "/api/reports/tasks/export", "departmentId", departmentId.toString())
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("Task report").contains("Department performance").contains("Overdue now,1");
		get(managerToken, "/api/reports/tasks/export", "format", "PDF").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FORMAT_NOT_AVAILABLE"));
	}

	@Test
	void workloadTicketAndProjectReportsFollowTheirPages() throws Exception {
		task("Plan", calendar.today().plusDays(3), 0);
		String body = as(managerToken, post("/api/projects"), "{\"name\":\"Report project\",\"departmentId\":%d}".formatted(departmentId))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		projects.add(((Number) JsonPath.read(body, "$.id")).longValue());

		get(managerToken, "/api/reports/workload", "departmentId", departmentId.toString()).andExpect(status().isOk())
			.andExpect(jsonPath("$.departments.length()").value(1))
			.andExpect(jsonPath("$.departments[0].department.id").value(departmentId))
			.andExpect(jsonPath("$.departments[0].activeTasks").value(1))
			.andExpect(jsonPath("$.employees.rows[?(@.user.id == " + employeeId + ")].activeTasks").value(Matchers.contains(1)));

		get(managerToken, "/api/reports/projects", "departmentId", departmentId.toString()).andExpect(status().isOk())
			.andExpect(jsonPath("$.projects.length()").value(1))
			.andExpect(jsonPath("$.projects[0].code").value(Matchers.startsWith("PRJ-")))
			.andExpect(jsonPath("$.byStatus.length()").value(5));

		get(managerToken, "/api/reports/tickets", "departmentId", departmentId.toString()).andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.created").value(0))
			.andExpect(jsonPath("$.priorities.length()").value(4))
			.andExpect(jsonPath("$.ageing.length()").value(5))
			.andExpect(jsonPath("$.ageing[0].label").value("Under 1 day"));

		String csv = get(managerToken, "/api/reports/projects/export", "departmentId", departmentId.toString())
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("Project progress report").contains("Report project");
	}

	private Long task(String title, LocalDate due, int actualHours) throws Exception {
		String json = "{\"title\":\"%s\",\"departmentId\":%d,\"assigneeId\":%d,\"priority\":\"MEDIUM\"%s}".formatted(title,
				departmentId, employeeId, due == null ? "" : ",\"dueDate\":\"" + due + "\"");
		String body = as(managerToken, post("/api/tasks"), json).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		tasks.add(id);
		if (actualHours > 0) {
			// Hours are logged later on a real task; set them directly for the report.
			jdbc.update("UPDATE tasks SET estimated_hours = 4, actual_hours = ? WHERE id = ?", actualHours, id);
		}
		return id;
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
