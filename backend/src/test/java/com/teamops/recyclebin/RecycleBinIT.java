package com.teamops.recyclebin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import com.jayway.jsonpath.JsonPath;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase X2 against MySQL: deleting moves a record to the recycle bin (hidden everywhere, children untouched), restore
 * brings it back exactly as it was, permanent delete is Super Admin only and confirmed by name, unique values stay
 * taken until the permanent delete, a child comes back only after its parent, expired items are purged
 * automatically, and every step is in the record's history. Runs in a throwaway department.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads",
		"app.scheduling.enabled=false" })
class RecycleBinIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private RecycleBinService recycleBin;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private final List<Long> tasks = new ArrayList<>();

	private Long departmentId;

	private Long managerId;

	private Long employeeId;

	private Long marketerId;

	private String adminToken;

	private String managerToken;

	private String employeeToken;

	private String outsiderManagerToken;

	private String marketerToken;

	private String nonce;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		nonce = Long.toHexString(System.nanoTime()).toUpperCase();
		String code = "BIN_" + nonce;
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Bin %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departmentId = ((Number) JsonPath.read(body, "$.id")).longValue();
		managerId = users.create(departmentId, RoleCodes.DEPARTMENT_MANAGER);
		employeeId = users.create(departmentId, RoleCodes.EMPLOYEE);
		marketerId = users.create(departmentId, RoleCodes.EMPLOYEE, "MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT");
		Long outsiderManagerId = users.create("IT", RoleCodes.DEPARTMENT_MANAGER);
		managerToken = login(users.email(managerId));
		employeeToken = login(users.email(employeeId));
		outsiderManagerToken = login(users.email(outsiderManagerId));
		marketerToken = login(users.email(marketerId));
	}

	@AfterEach
	void cleanUp() {
		// Deleted rows are invisible to the repositories, so clean up in SQL (children cascade).
		jdbc.update("DELETE FROM recycle_bin_items WHERE department_id = ? OR deleted_by IN (?, ?, ?)", departmentId,
				managerId, employeeId, marketerId);
		tasks.forEach(id -> jdbc.update("DELETE FROM tasks WHERE id = ?", id));
		String pages = "SELECT id FROM marketing_pages WHERE department_id = ?";
		jdbc.update("DELETE FROM marketing_keywords WHERE page_id IN (SELECT id FROM (" + pages + ") p)", departmentId);
		jdbc.update("DELETE FROM marketing_pages WHERE department_id = ?", departmentId);
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void aDeletedTaskIsHiddenAndComesBackWithItsChildren() throws Exception {
		Long taskId = createTask("Bin me " + nonce);
		as(employeeToken, post("/api/tasks/%d/comments".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"Keep this comment\"}")).andExpect(status().isOk());
		as(managerToken, post("/api/tasks/%d/checklist".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"Keep this item\"}")).andExpect(status().isOk());

		// Deleting needs TASK_DELETE: the employee's role does not have it.
		as(employeeToken, delete("/api/tasks/" + taskId)).andExpect(status().isForbidden());
		as(managerToken, delete("/api/tasks/" + taskId)).andExpect(status().isNoContent());

		// Gone from reads, lists and search; the row and its children are still there.
		as(managerToken, get("/api/tasks/" + taskId)).andExpect(status().isNotFound());
		as(managerToken, get("/api/tasks").param("search", "Bin me " + nonce)).andExpect(jsonPath("$.totalElements").value(0));
		assertThat(jdbc.queryForObject("SELECT deleted_by FROM tasks WHERE id = ?", Long.class, taskId)).isEqualTo(managerId);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_comments WHERE task_id = ?", Long.class, taskId)).isEqualTo(1);

		// In the bin for the Super Admin and this department's manager; not for others.
		Long itemId = binItem(managerToken, "TASK", taskId);
		as(adminToken, get("/api/recycle-bin").param("entityType", "TASK").param("search", "TSK"))
			.andExpect(status().isOk());
		as(outsiderManagerToken, get("/api/recycle-bin").param("entityType", "TASK"))
			.andExpect(jsonPath("$.content[?(@.entityId == %d)]".formatted(taskId)).isEmpty());
		as(employeeToken, get("/api/recycle-bin")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RECYCLE_BIN_FORBIDDEN"));
		as(outsiderManagerToken, post("/api/recycle-bin/%d/restore".formatted(itemId))).andExpect(status().isNotFound());

		// Restore: exactly as it was, children included.
		as(managerToken, post("/api/recycle-bin/%d/restore".formatted(itemId))).andExpect(status().isOk())
			.andExpect(jsonPath("$.entityType").value("TASK"))
			.andExpect(jsonPath("$.entityId").value(taskId));
		as(managerToken, get("/api/tasks/" + taskId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Bin me " + nonce))
			.andExpect(jsonPath("$.comments[0].body").value("Keep this comment"))
			.andExpect(jsonPath("$.checklist[0].content").value("Keep this item"));
		as(managerToken, post("/api/recycle-bin/%d/restore".formatted(itemId))).andExpect(status().isNotFound());

		// The history tells the story.
		List<String> actions = JsonPath.read(as(managerToken, get("/api/history/TASK/" + taskId).param("size", "100"))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.content[?(@.entityType == 'TASK')].action");
		assertThat(actions).contains("RECORD_DELETED", "RECORD_RESTORED", "TASK_DELETED", "RECORD_CREATED");
	}

	@Test
	void onlyASuperAdminDeletesPermanentlyAndMustNameTheItem() throws Exception {
		Long taskId = createTask("Purge me " + nonce);
		as(employeeToken, post("/api/tasks/%d/comments".formatted(taskId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"Goes too\"}")).andExpect(status().isOk());
		as(managerToken, delete("/api/tasks/" + taskId)).andExpect(status().isNoContent());
		Long itemId = binItem(adminToken, "TASK", taskId);
		String code = jdbc.queryForObject("SELECT code FROM tasks WHERE id = ?", String.class, taskId);

		as(managerToken, post("/api/recycle-bin/%d/purge".formatted(itemId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"%s\"}".formatted(code))).andExpect(status().isForbidden());
		as(adminToken, post("/api/recycle-bin/%d/purge".formatted(itemId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"something else\"}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CONFIRMATION_MISMATCH"));
		as(adminToken, post("/api/recycle-bin/%d/purge".formatted(itemId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\" %s \"}".formatted(code.toLowerCase()))).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE id = ?", Long.class, taskId)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_comments WHERE task_id = ?", Long.class, taskId)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recycle_bin_items WHERE id = ?", Long.class, itemId)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'TASK' AND entity_id = ? AND action = 'RECORD_PURGED'",
				Long.class, taskId)).isEqualTo(1);
	}

	@Test
	void uniqueValuesStayTakenAndAChildComesBackOnlyAfterItsParent() throws Exception {
		String url = "/it-bin/" + nonce.toLowerCase();
		Long pageId = id(as(marketerToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON)
			.content(page(url))).andExpect(status().isCreated()));
		Long keywordId = id(as(marketerToken, post("/api/marketing/keywords").contentType(MediaType.APPLICATION_JSON)
			.content("{\"pageId\":%d,\"keyword\":\"recycle bin %s\"}".formatted(pageId, nonce)))
			.andExpect(status().isCreated()));

		// The keyword goes first, then its page (a page with live keywords cannot be deleted).
		as(marketerToken, delete("/api/marketing/keywords/" + keywordId)).andExpect(status().isNoContent());
		as(marketerToken, delete("/api/marketing/pages/" + pageId)).andExpect(status().isNoContent());
		as(marketerToken, get("/api/marketing/pages/" + pageId)).andExpect(status().isNotFound());

		// The URL is still taken by the page in the bin.
		as(marketerToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON).content(page(url)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IN_RECYCLE_BIN"));

		// The keyword needs its page back first; the manager of the page's department may restore both.
		Long keywordItem = binItem(managerToken, "SEO_KEYWORD", keywordId);
		Long pageItem = binItem(managerToken, "SEO_PAGE", pageId);
		as(managerToken, post("/api/recycle-bin/%d/restore".formatted(keywordItem))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PARENT_IN_RECYCLE_BIN"));
		as(managerToken, post("/api/recycle-bin/%d/restore".formatted(pageItem))).andExpect(status().isOk());
		as(managerToken, post("/api/recycle-bin/%d/restore".formatted(keywordItem))).andExpect(status().isOk());
		as(marketerToken, get("/api/marketing/keywords/" + keywordId)).andExpect(status().isOk());

		// Released only by the permanent delete.
		as(marketerToken, delete("/api/marketing/keywords/" + keywordId)).andExpect(status().isNoContent());
		as(marketerToken, delete("/api/marketing/pages/" + pageId)).andExpect(status().isNoContent());
		purge(binItem(adminToken, "SEO_KEYWORD", keywordId));
		purge(binItem(adminToken, "SEO_PAGE", pageId));
		as(marketerToken, post("/api/marketing/pages").contentType(MediaType.APPLICATION_JSON).content(page(url)))
			.andExpect(status().isCreated());
	}

	@Test
	void expiredItemsArePurgedAutomatically() throws Exception {
		Long oldTask = createTask("Old " + nonce);
		Long recentTask = createTask("Recent " + nonce);
		as(managerToken, delete("/api/tasks/" + oldTask)).andExpect(status().isNoContent());
		as(managerToken, delete("/api/tasks/" + recentTask)).andExpect(status().isNoContent());
		jdbc.update("UPDATE recycle_bin_items SET deleted_at = TIMESTAMPADD(DAY, -91, UTC_TIMESTAMP(6)) "
				+ "WHERE entity_type = 'TASK' AND entity_id = ?", oldTask);

		int purged = recycleBin.purgeExpired(recycleBin::purgeExpiredItem);

		assertThat(purged).isGreaterThanOrEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE id = ?", Long.class, oldTask)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE id = ?", Long.class, recentTask)).isEqualTo(1);
		Map<String, Object> purgeEntry = jdbc.queryForMap(
				"SELECT actor_id FROM audit_logs WHERE entity_type = 'TASK' AND entity_id = ? AND action = 'RECORD_PURGED'",
				oldTask);
		assertThat(purgeEntry.get("actor_id")).as("the system").isNull();
		as(adminToken, get("/api/recycle-bin/catalog")).andExpect(jsonPath("$.retentionDays").value(90));
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void purge(Long itemId) throws Exception {
		String item = as(adminToken, get("/api/recycle-bin").param("size", "100")).andReturn().getResponse().getContentAsString();
		List<String> confirmations = JsonPath.read(item, "$.content[?(@.id == %d)].confirmationText".formatted(itemId));
		as(adminToken, post("/api/recycle-bin/%d/purge".formatted(itemId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"%s\"}".formatted(confirmations.get(0)))).andExpect(status().isNoContent());
	}

	private Long binItem(String token, String type, Long entityId) throws Exception {
		String body = as(token, get("/api/recycle-bin").param("entityType", type).param("size", "100"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> ids = JsonPath.read(body, "$.content[?(@.entityId == %d)].id".formatted(entityId));
		assertThat(ids).as(type + " #" + entityId + " in the bin").hasSize(1);
		return ids.get(0).longValue();
	}

	private Long createTask(String title) throws Exception {
		Long id = id(as(managerToken, post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
				{"title":"%s","departmentId":%d,"assigneeId":%d,"priority":"MEDIUM"}
				""".formatted(title, departmentId, employeeId))).andExpect(status().isCreated()));
		tasks.add(id);
		return id;
	}

	private String page(String url) {
		return "{\"url\":\"%s\",\"title\":\"Bin page\",\"pageType\":\"SERVICE\",\"departmentId\":%d,\"ownerId\":%d}"
			.formatted(url, departmentId, marketerId);
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
