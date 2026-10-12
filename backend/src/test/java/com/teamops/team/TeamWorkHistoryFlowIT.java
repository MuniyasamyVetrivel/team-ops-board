package com.teamops.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.task.repository.TaskRepository;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * A team member's work history against MySQL: who may look (Super Admin everyone, managers their department,
 * employees themselves), the status / overdue / date-range / priority / text filters, priority ordering, the summary
 * (counts, on-time %, average days, overdue, hours, weekly completions), tickets with SLA timeliness, and the CSV
 * export. Runs in a throwaway department with dates set relative to today. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class TeamWorkHistoryFlowIT {

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
	private TicketRepository ticketRepository;

	@Autowired
	private BusinessCalendar calendar;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private final List<Long> tasks = new ArrayList<>();

	private final List<Long> tickets = new ArrayList<>();

	private Long departmentId;

	private Long memberId;

	private String adminToken;

	private String managerToken;

	private String memberToken;

	private String outsiderToken;

	private String otherManagerToken;

	private LocalDate today;

	/** 40 days ago: the start of the history. */
	private LocalDate base;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		String code = "WH_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"History %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();
		Long managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		memberId = users.create(departmentId, RoleCodes.EMPLOYEE, "TICKET_EDIT");
		Long outsiderId = users.create("IT", RoleCodes.EMPLOYEE);
		Long otherManagerId = users.create("IT", RoleCodes.DEPARTMENT_MANAGER);
		managerToken = login(users.email(managerId));
		memberToken = login(users.email(memberId));
		outsiderToken = login(users.email(outsiderId));
		otherManagerToken = login(users.email(otherManagerId));
		today = calendar.today();
		base = today.minusDays(40);
	}

	@AfterEach
	void cleanUp() {
		tasks.forEach(taskRepository::deleteById);
		tickets.forEach(ticketRepository::deleteById);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void onlySuperAdminsManagersOfTheDepartmentAndThePersonThemselvesMayLook() throws Exception {
		String path = "/api/team/" + memberId + "/tasks";
		for (String token : List.of(adminToken, managerToken, memberToken)) {
			as(token, get(path)).andExpect(status().isOk());
			as(token, get(path + "/summary")).andExpect(status().isOk());
		}
		for (String token : List.of(outsiderToken, otherManagerToken)) {
			as(token, get(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORK_NOT_VISIBLE"));
			as(token, get("/api/team/" + memberId + "/tickets")).andExpect(status().isForbidden());
			as(token, get(path + "/export")).andExpect(status().isForbidden());
		}
		as(adminToken, get("/api/team/999999999/tasks")).andExpect(status().isNotFound());
	}

	@Test
	void tasksAreFilteredSortedSummarisedAndExported() throws Exception {
		Long onTime = task("Plan the audit", "LOW", "COMPLETED", base, base.plusDays(10), base.plusDays(10), "3");
		Long late = task("Ship the release", "URGENT", "COMPLETED", null, base.plusDays(10), base.plusDays(13), "5");
		Long overdue = task("Fix the login bug", "HIGH", "IN_PROGRESS", null, today.minusDays(2), null, null);
		Long upcoming = task("Write the handbook", "MEDIUM", "TODO", null, today.plusDays(5), null, null);
		task("Old idea", "LOW", "CANCELLED", null, null, null, null);
		String path = "/api/team/" + memberId + "/tasks";

		as(adminToken, get(path)).andExpect(jsonPath("$.totalElements").value(5));
		as(adminToken, get(path).param("status", "OVERDUE")).andExpect(jsonPath("$.content[*].id", contains(overdue.intValue())))
			.andExpect(jsonPath("$.content[0].overdue").value(true))
			.andExpect(jsonPath("$.content[0].daysLate").value(2));
		as(adminToken, get(path).param("status", "COMPLETED", "OVERDUE")).andExpect(jsonPath("$.totalElements").value(3));
		as(adminToken, get(path).param("dateField", "COMPLETED")
			.param("from", base.toString())
			.param("to", base.plusDays(10).toString())).andExpect(jsonPath("$.content[*].id", contains(onTime.intValue())))
			.andExpect(jsonPath("$.content[0].onTime").value(true));
		as(adminToken, get(path).param("dateField", "DUE").param("from", today.toString()).param("to", today.plusDays(10).toString()))
			.andExpect(jsonPath("$.content[*].id", contains(upcoming.intValue())));
		as(adminToken, get(path).param("priority", "URGENT")).andExpect(jsonPath("$.content[*].id", contains(late.intValue())))
			.andExpect(jsonPath("$.content[0].onTime").value(false))
			.andExpect(jsonPath("$.content[0].daysLate").value(3));
		as(adminToken, get(path).param("search", "handbook")).andExpect(jsonPath("$.content[*].id", contains(upcoming.intValue())));
		as(adminToken, get(path).param("sort", "priority,desc").param("size", "2"))
			.andExpect(jsonPath("$.content[*].id", contains(late.intValue(), overdue.intValue())))
			.andExpect(jsonPath("$.totalElements").value(5));
		as(adminToken, get(path).param("status", "DONE")).andExpect(status().isBadRequest());

		as(managerToken, get(path + "/summary").param("dateField", "COMPLETED")
			.param("from", base.toString())
			.param("to", base.plusDays(13).toString()))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.completed").value(2))
			.andExpect(jsonPath("$.completedOnTimePct").value(50.0))
			// Plan: started on day 0, done on day 10. Release: created 5 days before day 0, done on day 13.
			.andExpect(jsonPath("$.averageDaysToComplete").value(14.0))
			.andExpect(jsonPath("$.totalActualHours").value(8.0));
		String summary = as(memberToken, get(path + "/summary")).andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(5))
			.andExpect(jsonPath("$.byStatus.COMPLETED").value(2))
			.andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(1))
			.andExpect(jsonPath("$.byStatus.BLOCKED").value(0))
			.andExpect(jsonPath("$.overdue").value(1))
			.andExpect(jsonPath("$.completedPerWeek.length()").value(12))
			.andReturn().getResponse().getContentAsString();
		List<Integer> perWeek = JsonPath.read(summary, "$.completedPerWeek[*].completed");
		assertThat(perWeek.stream().mapToInt(Integer::intValue).sum()).as("both completions fall in the last 12 weeks").isEqualTo(2);

		String csv = as(managerToken, get(path + "/export").param("sort", "priority,desc")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("Task ID,Title,Project,Priority,Status,Start date,Due date,Completed date")
			.contains("Ship the release,,URGENT,COMPLETED,").contains("Late by 3 days")
			.contains("Fix the login bug,,HIGH,IN_PROGRESS,").contains("Overdue by 2 days");
		assertThat(csv.indexOf("Ship the release")).as("export follows the sort").isLessThan(csv.indexOf("Fix the login bug"));
	}

	@Test
	void ticketsShowSlaTimeliness() throws Exception {
		Long resolved = ticket("VPN keeps dropping");
		Long breached = ticket("Laptop will not boot");
		Instant now = calendar.now();
		jdbc.update("""
				UPDATE tickets SET status = 'RESOLVED', sla_start_at = ?, resolution_due_at = ?, resolved_at = ?
				WHERE id = ?
				""", utc(now.minusSeconds(3 * 86_400)), utc(now.minusSeconds(2 * 86_400)), utc(now.minusSeconds(3 * 86_400 - 3_600)),
				resolved);
		jdbc.update("UPDATE tickets SET status = 'IN_PROGRESS', sla_start_at = ?, resolution_due_at = ? WHERE id = ?",
				utc(now.minusSeconds(5 * 86_400)), utc(now.minusSeconds(2 * 86_400)), breached);
		String path = "/api/team/" + memberId + "/tickets";

		as(managerToken, get(path)).andExpect(jsonPath("$.totalElements").value(2));
		as(managerToken, get(path).param("status", "OVERDUE")).andExpect(jsonPath("$.content[*].id", contains(breached.intValue())))
			.andExpect(jsonPath("$.content[0].overdue").value(true))
			.andExpect(jsonPath("$.content[0].daysLate").value(2));
		as(managerToken, get(path).param("status", "RESOLVED")).andExpect(jsonPath("$.content[0].onTime").value(true));
		as(managerToken, get(path + "/summary")).andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.byStatus.RESOLVED").value(1))
			.andExpect(jsonPath("$.overdue").value(1))
			.andExpect(jsonPath("$.completed").value(1))
			.andExpect(jsonPath("$.completedOnTimePct").value(100.0))
			.andExpect(jsonPath("$.totalActualHours").doesNotExist());
		String csv = as(memberToken, get(path + "/export")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("Ticket ID,Subject,Category").contains("VPN keeps dropping").contains("Overdue by 2 days");
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	/** Creates a task assigned to the member, then sets its history directly (created 5 days before {@link #base}). */
	private Long task(String title, String priority, String status, LocalDate start, LocalDate due, LocalDate completed,
			String actualHours) throws Exception {
		String body = as(adminToken, post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"%s\",\"departmentId\":%d,\"assigneeId\":%d,\"priority\":\"%s\"}".formatted(title,
					departmentId, memberId, priority)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		tasks.add(id);
		jdbc.update("""
				UPDATE tasks SET status = ?, start_date = ?, due_date = ?, completed_at = ?, actual_hours = ?, created_at = ?
				WHERE id = ?
				""", status, start, due, completed == null ? null : utc(calendar.startOf(completed).plusSeconds(10 * 3_600)),
				actualHours, utc(calendar.startOf(base.minusDays(5)).plusSeconds(9 * 3_600)), id);
		return id;
	}

	/** A ticket in the member's department, assigned to them. */
	private Long ticket(String subject) throws Exception {
		String categories = as(adminToken, get("/api/tickets/categories")).andReturn().getResponse().getContentAsString();
		List<Integer> other = JsonPath.read(categories, "$[?(@.name == 'Other')].id");
		String body = as(adminToken, post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
			.content("{\"subject\":\"%s\",\"categoryId\":%d,\"departmentId\":%d,\"priority\":\"HIGH\"}".formatted(subject,
					other.getFirst(), departmentId)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		tickets.add(id);
		as(adminToken, put("/api/tickets/" + id + "/assignee").contentType(MediaType.APPLICATION_JSON)
			.content("{\"assigneeId\":%d}".formatted(memberId))).andExpect(status().isOk());
		return id;
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
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
