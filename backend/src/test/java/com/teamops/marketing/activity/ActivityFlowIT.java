package com.teamops.marketing.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.activity.entity.Frequency;
import com.teamops.marketing.activity.service.OccurrenceEngine;
import com.teamops.marketing.activity.service.Recurrence;
import com.teamops.marketing.activity.service.Recurrence.Period;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 13 end-to-end against MySQL: an activity generates its current occurrence with a task and checklist;
 * completing the task completes the occurrence and creates the next (idempotent through the unique key); reopening,
 * cancelling and deactivating; occurrences without a task completed and skipped by hand; the daily job and its
 * reminders. Runs in a throwaway department. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class ActivityFlowIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private OccurrenceEngine engine;

	@Autowired
	private BusinessCalendar calendar;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private Long departmentId;

	private Long adminId;

	private String adminToken;

	private Long managerId;

	private String managerToken;

	private Long assigneeId;

	private String assigneeToken;

	private String otherToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		String code = "ACT_" + Long.toHexString(System.nanoTime()).toUpperCase();
		departmentId = id(as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Activities %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated()));
		managerId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "MARKETING_EDIT");
		assigneeId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW");
		Long otherId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW");
		managerToken = login(users.email(managerId));
		assigneeToken = login(users.email(assigneeId));
		otherToken = login(users.email(otherId));
	}

	@AfterEach
	void cleanUp() {
		String activities = "SELECT id FROM marketing_activities WHERE department_id = ?";
		List<Long> taskIds = jdbc.queryForList("SELECT task_id FROM marketing_activity_occurrences WHERE task_id IS NOT NULL "
				+ "AND activity_id IN (" + activities + ")", Long.class, departmentId);
		jdbc.update("DELETE FROM marketing_activity_occurrences WHERE activity_id IN (SELECT id FROM (" + activities
				+ ") a)", departmentId);
		taskIds.forEach(id -> jdbc.update("DELETE FROM tasks WHERE id = ?", id));
		jdbc.update("DELETE FROM marketing_activities WHERE department_id = ?", departmentId);
		jdbc.update("DELETE FROM audit_logs WHERE actor_id IN (SELECT id FROM users WHERE department_id = ? OR id = ?)",
				departmentId, adminId);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void completingATaskCompletesTheOccurrenceAndCreatesTheNext() throws Exception {
		LocalDate today = calendar.today();
		Period current = Recurrence.containing(Frequency.MONTHLY, today);
		Period next = Recurrence.next(Frequency.MONTHLY, current);

		String created = as(managerToken, post("/api/marketing/activities").contentType(MediaType.APPLICATION_JSON)
			.content(activity("Monthly SEO Ranking Update", "MONTHLY", today.minusMonths(2), "\"Update {month} keyword rankings\"")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.occurrenceCount").value(1))
			.andExpect(jsonPath("$.locked").value(true))
			.andExpect(jsonPath("$.checklist.length()").value(2))
			.andExpect(jsonPath("$.nextOccurrence.periodStart").value(current.start().toString()))
			.andExpect(jsonPath("$.nextOccurrence.dueDate").value(Recurrence.dueDate(current, 4).toString()))
			.andExpect(jsonPath("$.nextOccurrence.status").value("PENDING"))
			.andExpect(jsonPath("$.nextOccurrence.assignee.id").value(assigneeId))
			.andExpect(jsonPath("$.nextOccurrence.canAct").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long activityId = ((Number) JsonPath.read(created, "$.id")).longValue();
		Long taskId = ((Number) JsonPath.read(created, "$.nextOccurrence.task.id")).longValue();
		String title = JsonPath.read(created, "$.nextOccurrence.task.title");
		assertThat(title).isEqualTo(Recurrence.title("Update {month} keyword rankings", Frequency.MONTHLY, current));

		// The generated task: the activity's department, assignee, due date and checklist, flagged as generated.
		assertThat(jdbc.queryForObject("SELECT source FROM tasks WHERE id = ?", String.class, taskId))
			.isEqualTo("MARKETING_ACTIVITY");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_checklists WHERE task_id = ?", Integer.class, taskId))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'TASK_ASSIGNED' "
				+ "AND entity_id = ?", Integer.class, assigneeId, taskId)).isEqualTo(1);

		// The assignee completes the task: the occurrence completes and next month's is created with its task.
		taskStatus(assigneeToken, taskId, "COMPLETED").andExpect(status().isOk());
		as(otherToken, get("/api/marketing/activities/" + activityId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.occurrenceCount").value(2))
			.andExpect(jsonPath("$.lastCompletedAt").isNotEmpty())
			.andExpect(jsonPath("$.nextOccurrence.periodStart").value(next.start().toString()))
			.andExpect(jsonPath("$.recentOccurrences[1].status").value("COMPLETED"))
			.andExpect(jsonPath("$.recentOccurrences[1].completedBy.id").value(assigneeId))
			.andExpect(jsonPath("$.nextTaskTitle").value(
					Recurrence.title("Update {month} keyword rankings", Frequency.MONTHLY, Recurrence.next(Frequency.MONTHLY, next))));

		// Reopening keeps the next occurrence; completing again does not create a duplicate.
		taskStatus(assigneeToken, taskId, "IN_PROGRESS").andExpect(status().isOk());
		as(otherToken, get("/api/marketing/activities/" + activityId)).andExpect(jsonPath("$.occurrenceCount").value(2))
			.andExpect(jsonPath("$.recentOccurrences[1].status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.recentOccurrences[1].completedAt").isEmpty());
		taskStatus(assigneeToken, taskId, "COMPLETED").andExpect(status().isOk());
		assertThat(occurrences(activityId)).isEqualTo(2);

		// Cancelling next month's task skips that occurrence and moves on.
		Long nextTask = jdbc.queryForObject("SELECT task_id FROM marketing_activity_occurrences WHERE activity_id = ? "
				+ "AND period_start = ?", Long.class, activityId, next.start());
		taskStatus(adminToken, nextTask, "CANCELLED").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT status FROM marketing_activity_occurrences WHERE task_id = ?", String.class,
				nextTask)).isEqualTo("SKIPPED");
		assertThat(occurrences(activityId)).isEqualTo(3);

		// History fixes the frequency; with history it is deactivated, not deleted, and then stops generating.
		as(managerToken, put("/api/marketing/activities/" + activityId).contentType(MediaType.APPLICATION_JSON)
			.content(update(activityId, "WEEKLY", true))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACTIVITY_IN_USE"));
		as(managerToken, delete("/api/marketing/activities/" + activityId)).andExpect(status().isConflict());
		as(managerToken, put("/api/marketing/activities/" + activityId).contentType(MediaType.APPLICATION_JSON)
			.content(update(activityId, "MONTHLY", false))).andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		Long thirdTask = jdbc.queryForObject("SELECT task_id FROM marketing_activity_occurrences WHERE activity_id = ? "
				+ "ORDER BY period_start DESC LIMIT 1", Long.class, activityId);
		taskStatus(adminToken, thirdTask, "COMPLETED").andExpect(status().isOk());
		assertThat(occurrences(activityId)).isEqualTo(3);

		// Audit trail of the configuration.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'MARKETING_ACTIVITY' "
				+ "AND entity_id = ? AND action IN ('MARKETING_ACTIVITY_CREATED', 'MARKETING_ACTIVITY_UPDATED')",
				Integer.class, activityId)).isEqualTo(2);
	}

	@Test
	void occurrencesWithoutATaskAreCompletedOnTheActivity() throws Exception {
		LocalDate today = calendar.today();
		Period week = Recurrence.containing(Frequency.WEEKLY, today);
		String created = as(managerToken, post("/api/marketing/activities").contentType(MediaType.APPLICATION_JSON)
			.content(activity("Weekly competitor check", "WEEKLY", today.minusWeeks(3), "null")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.nextOccurrence.task").isEmpty())
			.andExpect(jsonPath("$.nextTaskTitle").isEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long activityId = ((Number) JsonPath.read(created, "$.id")).longValue();
		Long occurrenceId = ((Number) JsonPath.read(created, "$.nextOccurrence.id")).longValue();

		// Only the activity's people (or a marketing manager) act on it.
		as(otherToken, post("/api/marketing/activity-occurrences/" + occurrenceId + "/complete"))
			.andExpect(status().isForbidden());
		as(assigneeToken, post("/api/marketing/activity-occurrences/" + occurrenceId + "/complete")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"notes\":\"Two new competitor pages\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.notes").value("Two new competitor pages"))
			.andExpect(jsonPath("$.dueState").value("NONE"));
		as(assigneeToken, post("/api/marketing/activity-occurrences/" + occurrenceId + "/skip"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OCCURRENCE_CLOSED"));

		// The next week is open and listed among what is due.
		String due = as(otherToken, get("/api/marketing/activity-occurrences").param("activityId", activityId.toString())
			.param("status", "PENDING")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(due, "$.totalElements")).isEqualTo(1);
		assertThat(JsonPath.<String>read(due, "$.content[0].periodStart"))
			.isEqualTo(Recurrence.next(Frequency.WEEKLY, week).start().toString());
		as(otherToken, get("/api/marketing/activity-occurrences").param("assigneeId", assigneeId.toString())
			.param("activityId", activityId.toString())).andExpect(jsonPath("$.totalElements").value(2));

		// Reopen; the next week is kept.
		as(managerToken, post("/api/marketing/activity-occurrences/" + occurrenceId + "/reopen")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(occurrences(activityId)).isEqualTo(2);

		// The daily job is idempotent for this activity, and reminders for taskless occurrences are sent once.
		engine.generateDue();
		engine.generateDue();
		assertThat(occurrences(activityId)).isEqualTo(2);
		engine.sendTasklessReminders();
		engine.sendTasklessReminders();
		int reminders = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? "
				+ "AND entity_type = 'MARKETING_ACTIVITY' AND entity_id = ?", Integer.class, assigneeId, activityId);
		LocalDate thisDue = Recurrence.dueDate(week, 4);
		assertThat(reminders).isEqualTo(thisDue.isAfter(today.plusDays(1)) ? 0 : 1);

		// A task-backed occurrence follows its task instead.
		String withTask = as(managerToken, post("/api/marketing/activities").contentType(MediaType.APPLICATION_JSON)
			.content(activity("Monthly backlink verification", "MONTHLY", today, "\"Verify {month} backlinks\"")))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		as(assigneeToken, post("/api/marketing/activity-occurrences/" + JsonPath.read(withTask, "$.nextOccurrence.id") + "/complete"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OCCURRENCE_HAS_TASK"));
	}

	@Test
	void anActivityThatStartsLaterHasNoOccurrenceYet() throws Exception {
		LocalDate start = Recurrence.next(Frequency.MONTHLY, Recurrence.containing(Frequency.MONTHLY, calendar.today())).start();
		String created = as(managerToken, post("/api/marketing/activities").contentType(MediaType.APPLICATION_JSON)
			.content(activity("Quarterly SEO audit", "QUARTERLY", start.plusMonths(3), "\"{quarter} {year} SEO audit\"")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.occurrenceCount").value(0))
			.andExpect(jsonPath("$.locked").value(false))
			.andExpect(jsonPath("$.nextOccurrence").isEmpty())
			.andReturn().getResponse().getContentAsString();
		Long activityId = ((Number) JsonPath.read(created, "$.id")).longValue();
		as(managerToken, delete("/api/marketing/activities/" + activityId)).andExpect(status().isNoContent());
		as(otherToken, get("/api/marketing/activities/" + activityId)).andExpect(status().isNotFound());
	}

	private int occurrences(Long activityId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM marketing_activity_occurrences WHERE activity_id = ?",
				Integer.class, activityId);
	}

	private String activity(String name, String frequency, LocalDate start, String template) {
		return """
				{"name":"%s","departmentId":%d,"ownerId":%d,"defaultAssigneeId":%d,"frequency":"%s","startDate":"%s",
				 "dueOffsetDays":4,"taskTitleTemplate":%s,"taskPriority":"HIGH",
				 "checklist":["Export positions","Record the month"]}
				""".formatted(name, departmentId, managerId, assigneeId, frequency, start, template);
	}

	private String update(Long activityId, String frequency, boolean active) throws Exception {
		String current = as(managerToken, get("/api/marketing/activities/" + activityId)).andReturn()
			.getResponse()
			.getContentAsString();
		return """
				{"version":%d,"name":"Monthly SEO Ranking Update","departmentId":%d,"ownerId":%d,"defaultAssigneeId":%d,
				 "frequency":"%s","startDate":"%s","dueOffsetDays":4,"taskTitleTemplate":"Update {month} keyword rankings",
				 "taskPriority":"HIGH","checklist":["Export positions","Record the month"],"active":%s}
				""".formatted(JsonPath.<Integer>read(current, "$.version"), departmentId, managerId, assigneeId, frequency,
				JsonPath.<String>read(current, "$.startDate"), active);
	}

	private ResultActions taskStatus(String token, Long taskId, String status) throws Exception {
		return as(token, put("/api/tasks/" + taskId + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"%s\"}".formatted(status)));
	}

	private static Long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
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
