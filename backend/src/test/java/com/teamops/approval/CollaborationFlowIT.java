package com.teamops.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
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
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.approval.repository.ApprovalRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.knowledge.repository.KnowledgeArticleRepository;
import com.teamops.project.repository.ProjectRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.task.repository.TaskRepository;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase 8 end-to-end against MySQL: projects, the approval workflow, announcements, the knowledge base (FULLTEXT
 * search), documents with versions, and the calendar merge. Runs in a throwaway department so seeded data never
 * changes the counts. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false",
		"app.storage.dir=target/it-uploads" })
class CollaborationFlowIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private ApprovalRepository approvalRepository;

	@Autowired
	private KnowledgeArticleRepository articleRepository;

	@Autowired
	private BusinessCalendar calendar;

	private IntegrationUsers users;

	private final List<Long> projects = new ArrayList<>();

	private final List<Long> tasks = new ArrayList<>();

	private final List<Long> articles = new ArrayList<>();

	private Long departmentId;

	private Long adminId;

	private Long managerId;

	private Long employeeId;

	private String adminToken;

	private String managerToken;

	private String employeeToken;

	private String outsiderToken;

	private String unique;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		unique = Long.toHexString(System.nanoTime()).toLowerCase();
		String code = "COL_" + unique.toUpperCase();
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Collab %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();
		managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		employeeId = users.create(departmentId, RoleCodes.EMPLOYEE);
		Long outsiderId = users.create("IT", RoleCodes.EMPLOYEE);
		as(adminToken, put("/api/departments/" + departmentId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Collab %s\",\"managerId\":%d,\"status\":\"ACTIVE\"}".formatted(code, managerId)))
			.andExpect(status().isOk());
		managerToken = login(users.email(managerId));
		employeeToken = login(users.email(employeeId));
		outsiderToken = login(users.email(outsiderId));
	}

	@AfterEach
	void cleanUp() {
		tasks.forEach(taskRepository::deleteById);
		projects.forEach(projectRepository::deleteById);
		articles.forEach(articleRepository::deleteById);
		approvalRepository.findAll()
			.stream()
			.filter(a -> a.getDepartment().getId().equals(departmentId))
			.forEach(approvalRepository::delete);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void projectsTrackProgressMilestonesRisksAndDependencies() throws Exception {
		LocalDate today = calendar.today();
		Long projectId = createProject("Website revamp " + unique);
		as(managerToken, get("/api/projects/" + projectId)).andExpect(jsonPath("$.code").value(matchesPattern("PRJ-\\d{4}")))
			.andExpect(jsonPath("$.progress").doesNotExist())
			.andExpect(jsonPath("$.permissions.canEdit").value(true));

		createTask(projectId, "Design mockups", true);
		createTask(projectId, "Build pages", false);
		String milestone = "{\"name\":\"Beta launch\",\"dueDate\":\"%s\"}".formatted(today.plusDays(5));
		as(managerToken, post("/api/projects/" + projectId + "/milestones").contentType(MediaType.APPLICATION_JSON)
			.content(milestone)).andExpect(status().isOk());
		as(managerToken, post("/api/projects/" + projectId + "/risks").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Vendor delay\",\"probability\":\"HIGH\",\"impact\":\"MEDIUM\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.risks[0].severity").value(6))
			.andExpect(jsonPath("$.progress").value(50))
			.andExpect(jsonPath("$.tasks.total").value(2))
			.andExpect(jsonPath("$.milestones[0].overdue").value(false));

		// The list carries the same computed figures.
		as(managerToken, get("/api/projects").param("search", unique)).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].progress").value(50))
			.andExpect(jsonPath("$.content[0].milestones.total").value(1))
			.andExpect(jsonPath("$.content[0].openRisks").value(1));

		// Department colleagues can look but not change; other departments cannot see it.
		as(employeeToken, get("/api/projects/" + projectId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions.canEdit").value(false));
		as(outsiderToken, get("/api/projects/" + projectId)).andExpect(status().isNotFound());

		// The open milestone shows on the department's calendar.
		assertThat(calendarKeys(employeeToken, today, today.plusDays(10)))
			.anyMatch(key -> key.startsWith("MILESTONE-"));

		// Dependencies reject self-references and loops.
		Long other = createProject("Hosting migration " + unique);
		as(managerToken, post("/api/projects/" + projectId + "/dependencies").contentType(MediaType.APPLICATION_JSON)
			.content("{\"projectId\":%d}".formatted(other))).andExpect(status().isOk());
		as(managerToken, post("/api/projects/" + other + "/dependencies").contentType(MediaType.APPLICATION_JSON)
			.content("{\"projectId\":%d}".formatted(projectId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEPENDENCY_CYCLE"));

		// A manual override replaces the task-based progress.
		int version = JsonPath.read(as(managerToken, get("/api/projects/" + projectId)).andReturn()
			.getResponse()
			.getContentAsString(), "$.version");
		as(managerToken, put("/api/projects/" + projectId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":%d,\"name\":\"Website revamp %s\",\"departmentId\":%d,\"status\":\"ACTIVE\",\"progressOverride\":80}"
				.formatted(version, unique, departmentId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progress").value(80));
	}

	@Test
	void approvalsMoveThroughTheirStepsAndNotifyTheRightPeople() throws Exception {
		String types = as(employeeToken, get("/api/approvals/types")).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Map<String, Object> otherType = JsonPath.<List<Map<String, Object>>>read(types, "$[?(@.code == 'OTHER')]")
			.get(0);
		long typeId = ((Number) otherType.get("id")).longValue();
		String original = restoreBody(JsonPath.read(types, "$[?(@.code == 'OTHER')].steps[*]"));

		// Workflow: department manager, then the Super Admin by name.
		as(managerToken, put("/api/approvals/types/" + typeId + "/steps").contentType(MediaType.APPLICATION_JSON)
			.content("{\"steps\":[{\"approverKind\":\"DEPARTMENT_MANAGER\"}]}")).andExpect(status().isForbidden());
		as(adminToken, put("/api/approvals/types/" + typeId + "/steps").contentType(MediaType.APPLICATION_JSON)
			.content("{\"steps\":[{\"approverKind\":\"DEPARTMENT_MANAGER\"},{\"approverKind\":\"USER\",\"userId\":%d}]}"
				.formatted(adminId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.steps.length()").value(2));
		try {
			LocalDate due = calendar.today().plusDays(3);
			Long id = submit(typeId, "Conference ticket", due);
			as(employeeToken, get("/api/approvals/" + id)).andExpect(jsonPath("$.code").value(matchesPattern("APR-\\d{6}")))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.currentStep").value(1))
				.andExpect(jsonPath("$.steps[0].approver.id").value(managerId))
				.andExpect(jsonPath("$.steps[0].status").value("PENDING"))
				.andExpect(jsonPath("$.steps[1].status").value("WAITING"))
				.andExpect(jsonPath("$.permissions.canCancel").value(true));
			as(outsiderToken, get("/api/approvals/" + id)).andExpect(status().isNotFound());
			assertThat(calendarKeys(employeeToken, due, due)).contains("APPROVAL-" + id);

			// The manager sees it in "to decide" and was notified; the requester cannot decide.
			as(managerToken, get("/api/approvals").param("view", "TO_DECIDE"))
				.andExpect(jsonPath("$.content[0].id").value(id))
				.andExpect(jsonPath("$.content[0].awaitingMe").value(true));
			as(managerToken, get("/api/notifications").param("unread", "true"))
				.andExpect(jsonPath("$.content[0].type").value("APPROVAL_REQUIRED"));
			decide(employeeToken, id, "APPROVE", null).andExpect(status().isForbidden());

			decide(managerToken, id, "APPROVE", "Fine by me").andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStep").value(2))
				.andExpect(jsonPath("$.steps[0].status").value("APPROVED"));
			decide(managerToken, id, "APPROVE", null).andExpect(status().isForbidden());
			decide(adminToken, id, "APPROVE", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));
			as(employeeToken, get("/api/notifications").param("unread", "true"))
				.andExpect(jsonPath("$.content[0].type").value("APPROVAL_DECIDED"))
				.andExpect(jsonPath("$.content[0].title").value(endsWith("was approved")));

			// Rejection needs a reason; cancelling is for the requester only.
			Long rejected = submit(typeId, "Standing desk", null);
			decide(managerToken, rejected, "REJECT", "").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
			decide(managerToken, rejected, "REJECT", "Not this quarter").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.steps[1].status").value("SKIPPED"));

			Long cancelled = submit(typeId, "Team lunch", null);
			as(managerToken, post("/api/approvals/" + cancelled + "/cancel")).andExpect(status().isForbidden());
			as(employeeToken, post("/api/approvals/" + cancelled + "/cancel")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

			as(managerToken, get("/api/dashboard")).andExpect(jsonPath("$.kpis.pendingApprovals").value(0));
		}
		finally {
			as(adminToken, put("/api/approvals/types/" + typeId + "/steps").contentType(MediaType.APPLICATION_JSON)
				.content(original)).andExpect(status().isOk());
		}
	}

	@Test
	void announcementsReachTheirAudienceAndTrackAcknowledgements() throws Exception {
		as(employeeToken, post("/api/announcements").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"x\",\"body\":\"y\",\"priority\":\"NORMAL\"}")).andExpect(status().isForbidden());
		as(managerToken, post("/api/announcements").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Everyone\",\"body\":\"y\",\"priority\":\"NORMAL\"}")).andExpect(status().isForbidden());

		String before = as(employeeToken, get("/api/announcements/unread-count")).andReturn().getResponse().getContentAsString();
		String created = as(managerToken, post("/api/announcements").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"New leave policy %s\",\"body\":\"Please read.\",\"targetDepartmentId\":%d,\"priority\":\"IMPORTANT\",\"ackRequired\":true}"
				.formatted(unique, departmentId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("ACTIVE"))
			.andExpect(jsonPath("$.stats.audience").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(created, "$.id")).longValue();

		String after = as(employeeToken, get("/api/announcements/unread-count")).andReturn().getResponse().getContentAsString();
		assertThat((int) JsonPath.read(after, "$.unread")).isEqualTo((int) JsonPath.read(before, "$.unread") + 1);
		as(employeeToken, get("/api/notifications").param("unread", "true"))
			.andExpect(jsonPath("$.content[0].type").value("ANNOUNCEMENT_PUBLISHED"));
		assertThat(announcementIds(employeeToken)).contains(id);
		assertThat(announcementIds(outsiderToken)).doesNotContain(id);
		as(outsiderToken, post("/api/announcements/" + id + "/read")).andExpect(status().isNotFound());

		as(employeeToken, post("/api/announcements/" + id + "/acknowledge")).andExpect(status().isNoContent());
		as(employeeToken, post("/api/announcements/" + id + "/acknowledge")).andExpect(status().isNoContent());
		String acked = as(employeeToken, get("/api/announcements/unread-count")).andReturn().getResponse().getContentAsString();
		assertThat((int) JsonPath.read(acked, "$.unread")).isEqualTo((int) JsonPath.read(before, "$.unread"));

		String list = as(managerToken, get("/api/announcements")).andReturn().getResponse().getContentAsString();
		Map<String, Object> mine = JsonPath.<List<Map<String, Object>>>read(list, "$.content[?(@.id == %d)]".formatted(id))
			.get(0);
		assertThat(mine).extractingByKey("stats").isEqualTo(Map.of("audience", 2, "read", 1, "acknowledged", 1));
		as(employeeToken, get("/api/announcements").param("state", "EXPIRED")).andExpect(status().isForbidden());
	}

	@Test
	void theKnowledgeBaseSearchesPublishedArticlesWithFullTextAndHidesDrafts() throws Exception {
		String word = "zyx" + unique;
		Long published = createArticle("Reset the office VPN " + unique, "Use the " + word + " profile to connect.",
				"PUBLISHED");
		Long draft = createArticle("Draft " + unique, "Unreleased " + word + " notes.", "DRAFT");
		String slug = JsonPath.read(as(managerToken, get("/api/knowledge-base/articles").param("search", word))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.content[?(@.id == %d)].slug".formatted(published)).toString().replaceAll("[\\[\\]\"]", "");

		// Employees find the published article by a word in its body (and by prefix), never the draft.
		as(employeeToken, get("/api/knowledge-base/articles").param("search", word))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(published));
		as(employeeToken, get("/api/knowledge-base/articles").param("search", word.substring(0, word.length() - 2)))
			.andExpect(jsonPath("$.totalElements").value(1));
		String draftSlug = articleRepository.findById(draft).orElseThrow().getSlug();
		as(employeeToken, get("/api/knowledge-base/articles/" + draftSlug)).andExpect(status().isNotFound());

		as(employeeToken, get("/api/knowledge-base/articles/" + slug)).andExpect(status().isOk())
			.andExpect(jsonPath("$.viewCount").value(1))
			.andExpect(jsonPath("$.canEdit").value(false));

		// The same title gets a distinct slug.
		Long twin = createArticle("Reset the office VPN " + unique, "Another body.", "DRAFT");
		assertThat(articleRepository.findById(twin).orElseThrow().getSlug()).isEqualTo(slug + "-2");
	}

	@Test
	void documentsKeepEveryVersionAndFollowDepartmentScope() throws Exception {
		String uploaded = as(managerToken, multipart("/api/documents").file(file("v1 content"))
			.param("name", "Leave policy " + unique)
			.param("departmentId", String.valueOf(departmentId))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.versions.length()").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(uploaded, "$.id")).longValue();

		as(managerToken, multipart("/api/documents/" + id + "/versions").file(file("v2 content"))
			.param("changeNote", "Updated carry-over rules")).andExpect(status().isOk())
			.andExpect(jsonPath("$.versions[0].versionNo").value(2))
			.andExpect(jsonPath("$.versions[0].changeNote").value("Updated carry-over rules"));

		as(employeeToken, get("/api/documents").param("search", unique)).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].versionCount").value(2))
			.andExpect(jsonPath("$.content[0].current.versionNo").value(2))
			.andExpect(jsonPath("$.content[0].canEdit").value(false));
		as(employeeToken, get("/api/documents/" + id + "/versions/1/download")).andExpect(status().isOk())
			.andExpect(content().string("v1 content"));
		as(outsiderToken, get("/api/documents/" + id)).andExpect(status().isNotFound());
		as(employeeToken, multipart("/api/documents/" + id + "/versions").file(file("nope")))
			.andExpect(status().isForbidden());

		as(managerToken, delete("/api/documents/" + id)).andExpect(status().isNoContent());
		as(managerToken, get("/api/documents/" + id)).andExpect(status().isNotFound());
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private Long createProject(String name) throws Exception {
		String body = as(managerToken, post("/api/projects").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"departmentId\":%d}".formatted(name, departmentId)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		projects.add(id);
		return id;
	}

	private void createTask(Long projectId, String title, boolean completed) throws Exception {
		String body = as(managerToken, post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"%s\",\"departmentId\":%d,\"projectId\":%d,\"assigneeId\":%d}".formatted(title,
					departmentId, projectId, employeeId)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		tasks.add(id);
		if (completed) {
			as(managerToken, put("/api/tasks/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"COMPLETED\"}")).andExpect(status().isOk());
		}
	}

	private Long submit(long typeId, String title, LocalDate due) throws Exception {
		String body = as(employeeToken, post("/api/approvals").contentType(MediaType.APPLICATION_JSON)
			.content("{\"typeId\":%d,\"title\":\"%s\"%s}".formatted(typeId, title,
					due == null ? "" : ",\"dueDate\":\"" + due + "\"")))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions decide(String token, Long id, String decision, String comment) throws Exception {
		return as(token, post("/api/approvals/" + id + "/decision").contentType(MediaType.APPLICATION_JSON)
			.content(comment == null ? "{\"decision\":\"%s\"}".formatted(decision)
					: "{\"decision\":\"%s\",\"comment\":\"%s\"}".formatted(decision, comment)));
	}

	/** Rebuilds a workflow request body from the type's current steps. */
	private static String restoreBody(List<Map<String, Object>> steps) {
		return steps.stream().map(step -> {
			StringBuilder json = new StringBuilder("{\"approverKind\":\"" + step.get("approverKind") + "\"");
			if (step.get("role") instanceof Map<?, ?> role) {
				json.append(",\"roleCode\":\"").append(role.get("code")).append('"');
			}
			if (step.get("user") instanceof Map<?, ?> user) {
				json.append(",\"userId\":").append(user.get("id"));
			}
			return json.append('}').toString();
		}).collect(Collectors.joining(",", "{\"steps\":[", "]}"));
	}

	private Long createArticle(String title, String body, String status) throws Exception {
		String response = as(managerToken, post("/api/knowledge-base/articles").contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"%s\",\"body\":\"%s\",\"categoryId\":3,\"status\":\"%s\",\"tags\":[\"vpn\"]}"
				.formatted(title, body, status)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long id = ((Number) JsonPath.read(response, "$.id")).longValue();
		articles.add(id);
		return id;
	}

	private List<Long> announcementIds(String token) throws Exception {
		String body = as(token, get("/api/announcements").param("size", "100")).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Number> ids = JsonPath.read(body, "$.content[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private List<String> calendarKeys(String token, LocalDate from, LocalDate to) throws Exception {
		String body = as(token, get("/api/calendar").param("from", from.toString()).param("to", to.toString()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.items[*].key");
	}

	private static MockMultipartFile file(String text) {
		return new MockMultipartFile("file", "leave-policy.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8));
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
