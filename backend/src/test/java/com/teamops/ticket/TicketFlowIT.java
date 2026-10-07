package com.teamops.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 7 end-to-end against MySQL: ticket lifecycle, scope, internal notes, the SLA clock, compliance, the SLA
 * policy admin and dashboard ticket KPIs. Tickets go to a throwaway department so seeded data never changes the
 * counts. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false",
		"app.storage.dir=target/it-uploads" })
class TicketFlowIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private final List<Long> tickets = new ArrayList<>();

	private Long departmentId;

	private Long agentId;

	private Long otherCategoryId;

	private String adminToken;

	private String managerToken;

	private String agentToken;

	private String requesterToken;

	private String outsiderToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		String code = "TKT_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Help desk %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();

		Long managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		agentId = users.create(departmentId, RoleCodes.EMPLOYEE, "TICKET_EDIT");
		Long requesterId = users.create("IT", RoleCodes.EMPLOYEE);
		Long outsiderId = users.create("IT", RoleCodes.EMPLOYEE);
		managerToken = login(users.email(managerId));
		agentToken = login(users.email(agentId));
		requesterToken = login(users.email(requesterId));
		outsiderToken = login(users.email(outsiderId));

		String categories = as(requesterToken, get("/api/tickets/categories")).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> other = JsonPath.read(categories, "$[?(@.name == 'Other')].id");
		otherCategoryId = other.get(0).longValue();
	}

	@AfterEach
	void cleanUp() {
		tickets.forEach(ticketRepository::deleteById);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void ticketLifecycleWithScopeInternalNotesAndThePausedClock() throws Exception {
		Long id = create(requesterToken, "VPN drops every hour", "URGENT");
		as(requesterToken, get("/api/tickets/" + id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(matchesPattern("TKT-\\d{6}")))
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.department.id").value(departmentId))
			.andExpect(jsonPath("$.slaPolicy").value("Urgent"))
			.andExpect(jsonPath("$.sla.overall").value("ON_TRACK"))
			.andExpect(jsonPath("$.permissions.canWork").value(false));

		// Only the requester and the handling team can see it.
		as(outsiderToken, get("/api/tickets/" + id)).andExpect(status().isNotFound());
		as(agentToken, get("/api/tickets").param("search", "VPN drops every hour")).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		as(outsiderToken, get("/api/tickets").param("search", "VPN drops every hour"))
			.andExpect(jsonPath("$.totalElements").value(0));

		// The manager assigns it to the agent: it opens, and the agent is notified.
		as(managerToken, put("/api/tickets/" + id + "/assignee").contentType(MediaType.APPLICATION_JSON)
			.content("{\"assigneeId\":%d}".formatted(agentId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OPEN"));
		as(agentToken, get("/api/notifications").param("unread", "true"))
			.andExpect(jsonPath("$.content[0].type").value("TICKET_ASSIGNED"))
			.andExpect(jsonPath("$.content[0].entityId").value(id));

		// An internal note is not a response and the requester never sees it.
		comment(agentToken, id, "Router firmware issue, checking with vendor", true);
		as(requesterToken, get("/api/tickets/" + id)).andExpect(jsonPath("$.comments.length()").value(0))
			.andExpect(jsonPath("$.firstRespondedAt").value(nullValue()));
		as(requesterToken, post("/api/tickets/" + id + "/comments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"sneaky\",\"internal\":true}")).andExpect(status().isForbidden());

		// The first public agent reply is the first response.
		comment(agentToken, id, "Can you tell us which office you are in?", false);
		as(requesterToken, get("/api/tickets/" + id)).andExpect(jsonPath("$.comments.length()").value(1))
			.andExpect(jsonPath("$.comments[0].internal").value(false))
			.andExpect(jsonPath("$.firstRespondedAt").value(notNullValue()))
			.andExpect(jsonPath("$.sla.firstResponse.met").value(true));

		// Waiting for the requester pauses the clock; their reply resumes it.
		move(agentToken, id, "WAITING_FOR_REQUESTER").andExpect(status().isOk())
			.andExpect(jsonPath("$.sla.resolution.paused").value(true));
		comment(requesterToken, id, "Chennai office, 3rd floor", false);
		as(agentToken, get("/api/tickets/" + id)).andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.sla.resolution.paused").value(false))
			.andExpect(jsonPath("$.comments.length()").value(3));

		// Requesters cannot resolve; agents can, and requesters confirm.
		move(requesterToken, id, "RESOLVED").andExpect(status().isForbidden());
		move(agentToken, id, "RESOLVED").andExpect(status().isOk())
			.andExpect(jsonPath("$.resolvedAt").value(notNullValue()))
			.andExpect(jsonPath("$.sla.resolution.met").value(true));
		move(requesterToken, id, "CLOSED").andExpect(status().isOk())
			.andExpect(jsonPath("$.closedAt").value(notNullValue()));

		// A closed ticket can only be reopened by a manager.
		move(requesterToken, id, "OPEN").andExpect(status().isForbidden());
		move(agentToken, id, "OPEN").andExpect(status().isForbidden());
		move(managerToken, id, "OPEN").andExpect(status().isOk())
			.andExpect(jsonPath("$.resolvedAt").value(nullValue()))
			.andExpect(jsonPath("$.history[0].field").value("reopened"));
		move(managerToken, id, "NEW").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

		Long audited = jdbc.queryForObject(
				"select count(*) from audit_logs where entity_type = 'TICKET' and entity_id = ?", Long.class, id);
		assertThat(audited).as("created, assigned and 4 status changes").isGreaterThanOrEqualTo(6);
	}

	@Test
	void breachedTicketsShowInTheListTheSlaSummaryAndTheDashboard() throws Exception {
		Long onTime = create(requesterToken, "Need access to the shared drive", "LOW");
		Long late = create(requesterToken, "Laptop screen flickers", "URGENT");
		// Backdate the urgent ticket by 5 hours: both its 1 h and 4 h targets are now missed.
		Instant start = Instant.now().minus(Duration.ofHours(5));
		jdbc.update("update tickets set sla_start_at = ?, first_response_due_at = ?, resolution_due_at = ? where id = ?",
				Timestamp.from(start), Timestamp.from(start.plus(Duration.ofHours(1))),
				Timestamp.from(start.plus(Duration.ofHours(4))), late);

		as(managerToken, get("/api/tickets").param("sort", "priority,desc")).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].id").value(late))
			.andExpect(jsonPath("$.content[0].sla.overall").value("BREACHED"))
			.andExpect(jsonPath("$.content[1].id").value(onTime))
			.andExpect(jsonPath("$.content[1].sla.overall").value("ON_TRACK"));

		as(managerToken, get("/api/sla/summary").param("days", "30")).andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(2))
			.andExpect(jsonPath("$.open").value(2))
			.andExpect(jsonPath("$.breached").value(1))
			.andExpect(jsonPath("$.onTrack").value(1))
			.andExpect(jsonPath("$.firstResponseCompliance").value(0))
			.andExpect(jsonPath("$.resolutionCompliance").value(0))
			.andExpect(jsonPath("$.atRisk.length()").value(1))
			.andExpect(jsonPath("$.atRisk[0].id").value(late))
			.andExpect(jsonPath("$.priorities[0].priority").value("URGENT"))
			.andExpect(jsonPath("$.priorities[0].openBreached").value(1));

		as(managerToken, get("/api/dashboard")).andExpect(jsonPath("$.kpis.openTickets").value(2))
			.andExpect(jsonPath("$.kpis.slaBreaches").value(1));
		as(requesterToken, get("/api/dashboard")).andExpect(jsonPath("$.kpis.openTickets").value(2));
		as(outsiderToken, get("/api/dashboard")).andExpect(jsonPath("$.kpis.openTickets").value(0));

		as(requesterToken, get("/api/tickets/my/summary")).andExpect(jsonPath("$.requestedOpen").value(2))
			.andExpect(jsonPath("$.assignedOpen").value(0));
	}

	@Test
	void slaPoliciesCanOnlyBeChangedBySlaManagersAndApplyToNewTicketsOnly() throws Exception {
		String policies = as(managerToken, get("/api/sla/policies")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[0].priority").value("URGENT"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Map<String, Object> high = JsonPath.<List<Map<String, Object>>>read(policies, "$[?(@.priority == 'HIGH')]")
			.get(0);
		long policyId = ((Number) high.get("id")).longValue();
		int version = ((Number) high.get("version")).intValue();
		int firstResponse = ((Number) high.get("firstResponseMinutes")).intValue();
		int resolution = ((Number) high.get("resolutionMinutes")).intValue();

		Long before = create(requesterToken, "Before the change", "HIGH");
		String dueBefore = JsonPath.read(as(requesterToken, get("/api/tickets/" + before)).andReturn()
			.getResponse()
			.getContentAsString(), "$.sla.resolution.dueAt");

		as(managerToken, put("/api/sla/policies/" + policyId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":%d,\"firstResponseMinutes\":30,\"resolutionMinutes\":60}".formatted(version)))
			.andExpect(status().isForbidden());
		String updated = as(adminToken, put("/api/sla/policies/" + policyId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":%d,\"firstResponseMinutes\":30,\"resolutionMinutes\":60}".formatted(version)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resolutionMinutes").value(60))
			.andReturn()
			.getResponse()
			.getContentAsString();
		try {
			as(requesterToken, get("/api/tickets/" + before))
				.andExpect(jsonPath("$.sla.resolution.dueAt").value(dueBefore));
			Long after = create(requesterToken, "After the change", "HIGH");
			String detail = as(requesterToken, get("/api/tickets/" + after)).andReturn()
				.getResponse()
				.getContentAsString();
			Instant created = Instant.parse(JsonPath.read(detail, "$.sla.firstResponse.dueAt"))
				.minus(Duration.ofMinutes(30));
			assertThat(Instant.parse(JsonPath.read(detail, "$.sla.resolution.dueAt")))
				.isEqualTo(created.plus(Duration.ofMinutes(60)));
		}
		finally {
			int newVersion = JsonPath.read(updated, "$.version");
			as(adminToken, put("/api/sla/policies/" + policyId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":%d,\"firstResponseMinutes\":%d,\"resolutionMinutes\":%d}".formatted(newVersion,
						firstResponse, resolution)))
				.andExpect(status().isOk());
		}
	}

	@Test
	void attachmentsAreSharedWithTheTeamAndServedAsDownloads() throws Exception {
		Long id = create(requesterToken, "Error when printing", "MEDIUM");
		MockMultipartFile file = new MockMultipartFile("file", "error.txt", "text/plain",
				"Printer error 0x42".getBytes(StandardCharsets.UTF_8));
		String detail = as(requesterToken, multipart("/api/tickets/" + id + "/attachments").file(file))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attachments[0].fileName").value("error.txt"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long fileId = ((Number) JsonPath.read(detail, "$.attachments[0].fileId")).longValue();

		as(agentToken, get("/api/tickets/" + id + "/attachments/" + fileId)).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, matchesPattern("attachment;.*")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(content().string("Printer error 0x42"));
		as(outsiderToken, get("/api/tickets/" + id + "/attachments/" + fileId)).andExpect(status().isNotFound());
	}

	private Long create(String token, String subject, String priority) throws Exception {
		String response = as(token, post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
			.content("{\"subject\":\"%s\",\"categoryId\":%d,\"departmentId\":%d,\"priority\":\"%s\"}".formatted(subject,
					otherCategoryId, departmentId, priority)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(response, "$.id")).longValue();
		tickets.add(id);
		return id;
	}

	private void comment(String token, Long id, String body, boolean internal) throws Exception {
		as(token, post("/api/tickets/" + id + "/comments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"%s\",\"internal\":%s}".formatted(body, internal))).andExpect(status().isOk());
	}

	private ResultActions move(String token, Long id, String status) throws Exception {
		return as(token, put("/api/tickets/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"%s\"}".formatted(status)));
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
