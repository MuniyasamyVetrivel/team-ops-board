package com.teamops.search;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 21 global search against MySQL, in a throwaway department: every group follows its module's permission and
 * scope (an employee finds only their own tasks and no marketing records; a manager finds the department's tasks; a
 * Super Admin finds leads). Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class GlobalSearchFlowIT {

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

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private final List<Long> tasks = new ArrayList<>();

	private Long departmentId;

	private Long adminId;

	private Long employeeId;

	private Long colleagueId;

	private String adminToken;

	private String managerToken;

	private String employeeToken;

	/** Unique word in every record this test creates. */
	private String nonce;

	@BeforeEach
	void setUp() throws Exception {
		nonce = "zq" + Long.toHexString(System.nanoTime());
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		String code = "SRCH_" + Long.toHexString(System.nanoTime()).toUpperCase();
		String body = as(adminToken, post("/api/departments"), "{\"name\":\"Search %s\",\"code\":\"%s\"}".formatted(code, code))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();
		Long managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		employeeId = users.create(departmentId, RoleCodes.EMPLOYEE);
		colleagueId = users.create(departmentId, RoleCodes.EMPLOYEE);
		managerToken = login(users.email(managerId));
		employeeToken = login(users.email(employeeId));
	}

	@AfterEach
	void cleanUp() {
		tasks.forEach(taskRepository::deleteById);
		jdbc.update("DELETE FROM marketing_leads WHERE name LIKE ?", nonce + "%");
		jdbc.update("DELETE FROM audit_logs WHERE actor_id = ? AND action LIKE 'LEAD_%'", adminId);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void tasksFollowTheViewersScope() throws Exception {
		task("Invoice " + nonce, employeeId);
		task("Budget " + nonce, colleagueId);

		search(employeeToken, nonce).andExpect(status().isOk())
			.andExpect(jsonPath("$.query").value(nonce))
			.andExpect(jsonPath("$.groups[0].group").value("TASKS"))
			.andExpect(jsonPath("$.groups[0].total").value(1))
			.andExpect(jsonPath("$.groups[0].hits[0].type").value("TASK"))
			.andExpect(jsonPath("$.groups[0].hits[0].title").value("Invoice " + nonce))
			.andExpect(jsonPath("$.groups[0].hits[0].code").value(startsWith("TSK-")))
			.andExpect(jsonPath("$.groups[0].hits[0].status").value("TODO"));
		search(managerToken, nonce).andExpect(jsonPath("$.groups[0].group").value("TASKS"))
			.andExpect(jsonPath("$.groups[0].total").value(2));
		// Matching ignores case.
		search(employeeToken, "Invoice " + nonce.toUpperCase()).andExpect(jsonPath("$.groups[0].total").value(1));
	}

	@Test
	void marketingResultsNeedMarketingPermissions() throws Exception {
		as(adminToken, post("/api/marketing/leads"), """
				{"name":"%s Lead","company":"Acme","source":"ORGANIC","leadDate":"%s","ownerId":%d}
				""".formatted(nonce, calendar.today(), adminId)).andExpect(status().isCreated());

		search(adminToken, nonce).andExpect(jsonPath("$.groups[*].group").value(hasItem("LEADS")))
			.andExpect(jsonPath("$.groups[?(@.group == 'LEADS')].hits[0].code").value(hasItem(startsWith("LEAD-"))))
			.andExpect(jsonPath("$.groups[?(@.group == 'LEADS')].hits[0].subtitle").value(hasItem("Acme · Organic")));
		search(employeeToken, nonce).andExpect(jsonPath("$.groups[*].group").value(not(hasItem("LEADS"))));
	}

	@Test
	void employeesAreFoundByNameOrEmail() throws Exception {
		String emailStart = users.email(employeeId).substring(0, 15);
		search(employeeToken, emailStart).andExpect(jsonPath("$.groups[0].group").value("EMPLOYEES"))
			.andExpect(jsonPath("$.groups[0].total").value(1))
			.andExpect(jsonPath("$.groups[0].hits[0].id").value(employeeId));
		// Too short to search.
		search(employeeToken, "z").andExpect(jsonPath("$.groups.length()").value(0));
	}

	private void task(String title, Long assigneeId) throws Exception {
		String body = as(managerToken, post("/api/tasks"),
				"{\"title\":\"%s\",\"departmentId\":%d,\"assigneeId\":%d,\"priority\":\"MEDIUM\"}".formatted(title,
						departmentId, assigneeId))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		tasks.add(((Number) JsonPath.read(body, "$.id")).longValue());
	}

	private ResultActions search(String token, String query) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/search")
			.param("q", query)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
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
