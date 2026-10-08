package com.teamops.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.task.entity.TaskStatus;
import com.teamops.task.repository.TaskReminderRow;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 6 end-to-end against MySQL: dashboard aggregates per scope, assignment notifications and the merged
 * calendar. Runs in a throwaway department so seeded data never changes the counts. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class DashboardFlowIT {

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
	private BusinessCalendar calendar;

	private IntegrationUsers users;

	private final List<Long> tasks = new ArrayList<>();

	private Long departmentId;

	private Long employeeId;



	private String managerToken;

	private String employeeToken;

	private String outsiderToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		String adminToken = login(users.email(adminId));
		String code = "DSH_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Dashboard %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();

		Long managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		employeeId = users.create(departmentId, RoleCodes.EMPLOYEE);
		Long outsiderId = users.create("IT", RoleCodes.EMPLOYEE);
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
	void dashboardCountsFollowTheViewersScope() throws Exception {
		LocalDate today = calendar.today();
		Long overdue = create(today.minusDays(2), "Overdue report");
		Long dueToday = create(today, "Due today");
		create(today.plusDays(3), "Later this week");
		Long done = create(today.plusDays(1), "Finished early");
		as(employeeToken, put("/api/tasks/" + done + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"COMPLETED\"}")).andExpect(status().isOk());

		// Manager: their department, with team and department sections.
		as(managerToken, get("/api/dashboard")).andExpect(status().isOk())
			.andExpect(jsonPath("$.scope").value("DEPARTMENTS"))
			.andExpect(jsonPath("$.kpis.openTasks").value(3))
			.andExpect(jsonPath("$.kpis.overdue").value(1))
			.andExpect(jsonPath("$.kpis.dueToday").value(1))
			.andExpect(jsonPath("$.kpis.completedThisWeek").value(1))
			.andExpect(jsonPath("$.kpis.teamMembers").value(2))
			.andExpect(jsonPath("$.kpis.openTickets").value(0))
			.andExpect(jsonPath("$.kpis.pendingApprovals").value(0))
			.andExpect(jsonPath("$.departments.length()").value(1))
			.andExpect(jsonPath("$.departments[0].openTasks").value(3))
			.andExpect(jsonPath("$.departments[0].completed").value(1))
			.andExpect(jsonPath("$.departments[0].onTimePercent").value(100))
			.andExpect(jsonPath("$.departments[0].people").value(2))
			.andExpect(jsonPath("$.overdueTasks.length()").value(1))
			.andExpect(jsonPath("$.overdueTasks[0].id").value(overdue))
			.andExpect(jsonPath("$.upcomingTasks.length()").value(2))
			.andExpect(jsonPath("$.upcomingTasks[0].id").value(dueToday))
			.andExpect(jsonPath("$.weeklyCompletion.length()").value(8))
			.andExpect(jsonPath("$.weeklyCompletion[7].completed").value(1))
			.andExpect(jsonPath("$.statusDistribution[4].status").value("COMPLETED"))
			.andExpect(jsonPath("$.statusDistribution[4].count").value(1))
			.andExpect(jsonPath("$.recentActivity[0].taskId").value(done))
			.andExpect(jsonPath("$.recentActivity[0].newValue").value("COMPLETED"));

		// Employee: only their own work, no team sections.
		as(employeeToken, get("/api/dashboard")).andExpect(status().isOk())
			.andExpect(jsonPath("$.scope").value("OWN"))
			.andExpect(jsonPath("$.kpis.openTasks").value(3))
			.andExpect(jsonPath("$.kpis.teamMembers").doesNotExist())
			.andExpect(jsonPath("$.departments.length()").value(0))
			.andExpect(jsonPath("$.workload.rows.length()").value(1));

		// Someone in another department sees none of it.
		as(outsiderToken, get("/api/dashboard")).andExpect(status().isOk())
			.andExpect(jsonPath("$.kpis.openTasks").value(0))
			.andExpect(jsonPath("$.overdueTasks.length()").value(0))
			.andExpect(jsonPath("$.recentActivity.length()").value(0));

		// The reminder job would pick up the overdue and due-today tasks.
		List<Long> candidates = taskRepository.findReminderCandidates(TaskStatus.ACTIVE, today.plusDays(1))
			.stream()
			.map(TaskReminderRow::getId)
			.toList();
		assertThat(candidates).contains(overdue, dueToday);
	}

	@Test
	void assignmentsNotifyTheAssigneeOnly() throws Exception {
		Long taskId = create(calendar.today().plusDays(5), "Write release notes");

		as(employeeToken, get("/api/notifications/unread-count")).andExpect(jsonPath("$.unread").value(1));
		as(managerToken, get("/api/notifications/unread-count")).andExpect(jsonPath("$.unread").value(0));
		String list = as(employeeToken, get("/api/notifications").param("unread", "true")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].type").value("TASK_ASSIGNED"))
			.andExpect(jsonPath("$.content[0].entityId").value(taskId))
			.andExpect(jsonPath("$.content[0].read").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long notificationId = ((Number) JsonPath.read(list, "$.content[0].id")).longValue();

		as(outsiderToken, post("/api/notifications/" + notificationId + "/read")).andExpect(status().isNotFound());
		as(employeeToken, post("/api/notifications/" + notificationId + "/read")).andExpect(status().isOk())
			.andExpect(jsonPath("$.read").value(true));
		as(employeeToken, get("/api/notifications/unread-count")).andExpect(jsonPath("$.unread").value(0));

		// Assigning a task to yourself does not notify you.
		as(employeeToken, post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"My own task\",\"assigneeId\":%d}".formatted(employeeId)))
			.andExpect(status().isCreated())
			.andDo(result -> tasks.add(((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id"))
				.longValue()));
		as(employeeToken, get("/api/notifications/unread-count")).andExpect(jsonPath("$.unread").value(0));
	}

	@Test
	void calendarMergesVisibleEventsAndDeadlines() throws Exception {
		LocalDate today = calendar.today();
		Long taskId = create(today.plusDays(2), "Ship the dashboard");
		String event = """
				{"title":"Team offsite","eventType":"TEAM_EVENT","allDay":true,"startDate":"%s","endDate":"%s",
				 "departmentId":%d}
				""".formatted(today.plusDays(1), today.plusDays(1), departmentId);

		as(employeeToken, post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON).content(event))
			.andExpect(status().isForbidden());
		as(managerToken, post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Holiday\",\"eventType\":\"IMPORTANT_DATE\",\"allDay\":true,\"startDate\":\"%s\",\"endDate\":\"%s\"}"
				.formatted(today, today))).andExpect(status().isForbidden());
		String created = as(managerToken,
				post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON).content(event))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.startDate").value(today.plusDays(1).toString()))
			.andExpect(jsonPath("$.endDate").value(today.plusDays(1).toString()))
			.andExpect(jsonPath("$.canEdit").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long eventId = ((Number) JsonPath.read(created, "$.id")).longValue();

		List<String> employeeKeys = keys(employeeToken, today, today.plusDays(7), true);
		assertThat(employeeKeys).contains("EVENT-" + eventId, "TASK-" + taskId);
		List<String> outsiderKeys = keys(outsiderToken, today, today.plusDays(7), false);
		assertThat(outsiderKeys).doesNotContain("EVENT-" + eventId, "TASK-" + taskId);
		as(outsiderToken, get("/api/calendar/events/" + eventId)).andExpect(status().isNotFound());

		as(managerToken, get("/api/calendar").param("from", today.toString())
			.param("to", today.plusDays(200).toString())).andExpect(status().isBadRequest());
		as(managerToken, delete("/api/calendar/events/" + eventId)).andExpect(status().isNoContent());
		assertThat(keys(employeeToken, today, today.plusDays(7), true)).doesNotContain("EVENT-" + eventId);
	}

	private List<String> keys(String token, LocalDate from, LocalDate to, boolean mine) throws Exception {
		String body = as(token, get("/api/calendar").param("from", from.toString())
			.param("to", to.toString())
			.param("mine", String.valueOf(mine))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.items[*].key");
	}

	/** Manager creates a task in the throwaway department, assigned to the employee. */
	private Long create(LocalDate due, String title) throws Exception {
		String response = as(managerToken, post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"%s\",\"departmentId\":%d,\"assigneeId\":%d,\"dueDate\":\"%s\",\"estimatedHours\":4}"
				.formatted(title, departmentId, employeeId, due)))
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
