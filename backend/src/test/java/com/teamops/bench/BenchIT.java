package com.teamops.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Bench Management part 1 (Phase X6) end to end against MySQL, in a throwaway department with the module switched on:
 * consultants linked to Employee records, clients and engagements, permissions and the module switch, audit history,
 * the recycle bin, and CSV import and export.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads" })
class BenchIT {

	private static final String API = "/api/modules/bench";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private String nonce;

	private Long departmentId;

	private Long viewerId;

	private String adminToken;

	private String viewerToken;

	private String plainToken;

	private String outsiderToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(adminId);
		nonce = Long.toHexString(System.nanoTime()).toUpperCase();
		String code = "BENCH_" + nonce;
		departmentId = id(as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Bench %s\",\"code\":\"%s\"}".formatted(code, code))).andExpect(status().isCreated()));
		as(adminToken, put("/api/admin/modules/bench/departments/" + departmentId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":true}")).andExpect(status().isOk());

		viewerId = users.create(departmentId, RoleCodes.EMPLOYEE, "BENCH_VIEW");
		viewerToken = login(viewerId);
		plainToken = login(users.create(departmentId, RoleCodes.EMPLOYEE));
		// Holds both permissions, but the module is not switched on for the IT department.
		outsiderToken = login(users.create("IT", RoleCodes.EMPLOYEE, "BENCH_VIEW", "BENCH_EDIT"));
	}

	@AfterEach
	void cleanUp() {
		// Deleted rows are invisible to the repositories, so clean up in SQL (skills and certifications cascade).
		String employees = "SELECT id FROM employees WHERE employee_code LIKE '" + "BEN-" + nonce + "%'";
		String consultants = "SELECT id FROM bench_consultants WHERE employee_id IN (" + employees + ")";
		String clients = "SELECT id FROM bench_clients WHERE name LIKE '%" + nonce + "%'";
		String engagements = "SELECT id FROM bench_client_engagements WHERE client_id IN (" + clients + ")";
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'CONSULTANT' AND entity_id IN (" + consultants + ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'CLIENT_ENGAGEMENT' AND entity_id IN (" + engagements
				+ ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'CLIENT' AND entity_id IN (" + clients + ")");
		jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'EMPLOYEE' AND entity_id IN (" + employees + ")");
		jdbc.update("DELETE FROM bench_consultants WHERE employee_id IN (SELECT id FROM (" + employees + ") e)");
		jdbc.update("DELETE FROM bench_client_engagements WHERE client_id IN (SELECT id FROM (" + clients + ") c)");
		jdbc.update("DELETE FROM bench_clients WHERE name LIKE ?", "%" + nonce + "%");
		jdbc.update("DELETE FROM employees WHERE employee_code LIKE ?", "BEN-" + nonce + "%");
		users.deleteAll();
		departmentRepository.deleteById(departmentId);
	}

	@Test
	void consultantsFollowTheirEmployeeRecordThroughEditsAndTheRecycleBin() throws Exception {
		Long employeeId = createEmployee("1");
		String body = """
				{"employeeId":%d,"experienceYears":6.5,"location":"Chennai","skills":["SAP S/4HANA","Tosca"],
				"certifications":[{"name":"ISTQB Foundation","issuer":"ISTQB","issuedOn":"2020-05-01"}]}
				""".formatted(employeeId);
		Long consultantId = id(as(adminToken, post(API + "/consultants").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.employee.employeeCode").value(code("1")))
			.andExpect(jsonPath("$.employee.fullName").value("Bench " + nonce))
			.andExpect(jsonPath("$.status").value("ON_BENCH"))
			.andExpect(jsonPath("$.experienceYears").value(6.5))
			.andExpect(jsonPath("$.certifications[0].expired").value(false)));
		as(adminToken, post(API + "/consultants").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONSULTANT_EXISTS"));

		// BENCH_VIEW reads; changing needs BENCH_EDIT; no permission or no switch for the department means no access.
		as(viewerToken, get(API + "/consultants").param("search", nonce)).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		as(viewerToken, get(API + "/consultants").param("skill", "tosca")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(consultantId)).isNotEmpty());
		as(viewerToken, get(API + "/consultants").param("search", nonce).param("status", "NOTICE_PERIOD"))
			.andExpect(jsonPath("$.totalElements").value(0));
		as(viewerToken, put(API + "/consultants/" + consultantId).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());
		as(plainToken, get(API + "/consultants")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		as(outsiderToken, get(API + "/consultants")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));

		// Edits need the current version; skills keep their rows across a change of case.
		String update = """
				{"employeeId":%d,"employmentStatus":"NOTICE_PERIOD","skills":["tosca","Test automation"],"version":%d}
				""";
		as(adminToken, put(API + "/consultants/" + consultantId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(employeeId, 7))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(adminToken, put(API + "/consultants/" + consultantId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(employeeId, 0))).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("NOTICE_PERIOD"))
			.andExpect(jsonPath("$.skills[0]").value("tosca"))
			.andExpect(jsonPath("$.skills[1]").value("Test automation"))
			.andExpect(jsonPath("$.certifications").isEmpty());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bench_consultant_skills WHERE consultant_id = ?",
				Integer.class, consultantId)).isEqualTo(2);

		// History tells the story, children included.
		List<String> actions = JsonPath.read(as(adminToken, get("/api/history/CONSULTANT/" + consultantId)
			.param("size", "100")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
				"$.content[*].action");
		assertThat(actions).contains("RECORD_CREATED", "RECORD_UPDATED", "CONSULTANT_CREATED", "CONSULTANT_UPDATED");

		// Deleted into the recycle bin; the employee stays taken until the consultant is purged.
		as(adminToken, delete(API + "/consultants/" + consultantId)).andExpect(status().isNoContent());
		as(viewerToken, get(API + "/consultants/" + consultantId)).andExpect(status().isNotFound());
		as(adminToken, post(API + "/consultants").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IN_RECYCLE_BIN"));
		Long item = binItem("CONSULTANT", consultantId);
		as(adminToken, get("/api/recycle-bin").param("entityType", "CONSULTANT").param("search", nonce))
			.andExpect(jsonPath("$.content[0].label").value(code("1") + " Bench " + nonce))
			.andExpect(jsonPath("$.content[0].module").value("BENCH"))
			.andExpect(jsonPath("$.content[0].department.id").value(departmentId));
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(item))).andExpect(status().isOk());
		as(viewerToken, get(API + "/consultants/" + consultantId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.skills.length()").value(2));

		// Its employee goes to the bin: the consultant still reads and can be deleted, but not edited or restored.
		as(adminToken, delete("/api/celebrations/employees/" + employeeId)).andExpect(status().isNoContent());
		as(viewerToken, get(API + "/consultants/" + consultantId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.employee").doesNotExist())
			.andExpect(jsonPath("$.employeeId").value(employeeId));
		as(adminToken, put(API + "/consultants/" + consultantId).contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(employeeId, version(API + "/consultants/" + consultantId))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMPLOYEE_IN_RECYCLE_BIN"));
		Long employeeItem = binItem("EMPLOYEE", employeeId);
		as(adminToken, post("/api/recycle-bin/%d/purge".formatted(employeeItem)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirm\":\"Bench %s\"}".formatted(nonce))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PURGE_BLOCKED"));
		as(adminToken, delete(API + "/consultants/" + consultantId)).andExpect(status().isNoContent());
		item = binItem("CONSULTANT", consultantId);
		as(adminToken, get("/api/recycle-bin/%d/restore-check".formatted(item))).andExpect(status().isOk())
			.andExpect(jsonPath("$.canRestore").value(false));
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(item))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESTORE_CONFLICT"));
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(employeeItem))).andExpect(status().isOk());
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(item))).andExpect(status().isOk());
		as(viewerToken, get(API + "/consultants/" + consultantId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.employee.employeeCode").value(code("1")));
	}

	@Test
	void clientsAndEngagementsKeepNamesUniqueAndTheirStatusFollowsTheDates() throws Exception {
		Long clientId = id(as(adminToken, post(API + "/clients").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme %s\",\"location\":\"Sydney\"}".formatted(nonce))).andExpect(status().isCreated()));
		as(adminToken, post(API + "/clients").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"ACME %s\"}".formatted(nonce))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CLIENT_NAME_TAKEN"));
		as(viewerToken, post(API + "/clients").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Viewer %s\"}".formatted(nonce))).andExpect(status().isForbidden());

		Long ended = createEngagement(clientId, "Rollout", "\"2000-01-01\"", "\"2000-12-31\"", null);
		Long upcoming = createEngagement(clientId, "Upgrade", "\"2099-01-01\"", "null", null);
		Long active = createEngagement(clientId, "Support", "\"2000-01-01\"", "null", viewerId);
		as(adminToken, get(API + "/engagements/" + ended)).andExpect(jsonPath("$.status").value("ENDED"));
		as(adminToken, get(API + "/engagements/" + upcoming)).andExpect(jsonPath("$.status").value("UPCOMING"));
		as(viewerToken, get(API + "/engagements/" + active)).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.client.id").value(clientId))
			.andExpect(jsonPath("$.accountManager.id").value(viewerId));
		as(viewerToken, get(API + "/engagements").param("clientId", clientId.toString()).param("status", "ACTIVE"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(active));
		as(adminToken, post(API + "/engagements").contentType(MediaType.APPLICATION_JSON)
			.content(engagement(clientId, "rollout", "\"2001-01-01\"", "null", null))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ENGAGEMENT_NAME_TAKEN"));
		as(adminToken, post(API + "/engagements").contentType(MediaType.APPLICATION_JSON)
			.content(engagement(clientId, "Backwards", "\"2001-02-01\"", "\"2001-01-01\"", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATES"));
		as(viewerToken, get(API + "/clients").param("search", nonce)).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].engagementCount").value(3));

		// A client goes to the bin only without engagements, and an engagement comes back only with its client.
		as(adminToken, delete(API + "/clients/" + clientId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CLIENT_HAS_ENGAGEMENTS"));
		for (Long engagementId : List.of(ended, upcoming, active)) {
			as(adminToken, delete(API + "/engagements/" + engagementId)).andExpect(status().isNoContent());
		}
		as(adminToken, delete(API + "/clients/" + clientId)).andExpect(status().isNoContent());
		as(adminToken, post(API + "/clients").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"acme %s\"}".formatted(nonce))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IN_RECYCLE_BIN"));
		Long engagementItem = binItem("CLIENT_ENGAGEMENT", active);
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(engagementItem))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESTORE_CONFLICT"));
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(binItem("CLIENT", clientId))))
			.andExpect(status().isOk());
		as(adminToken, post("/api/recycle-bin/%d/restore".formatted(engagementItem))).andExpect(status().isOk());
		as(adminToken, get(API + "/engagements/" + active)).andExpect(status().isOk());

		// Edits need the current version and are audited field by field.
		as(adminToken, put(API + "/clients/" + clientId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme %s\",\"status\":\"INACTIVE\",\"version\":5}".formatted(nonce)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_UPDATE"));
		as(adminToken, put(API + "/clients/" + clientId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme %s\",\"status\":\"INACTIVE\",\"version\":%d}".formatted(nonce,
					version(API + "/clients/" + clientId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("INACTIVE"));
		assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(details, '$.changes.status.to')) FROM audit_logs "
				+ "WHERE action = 'CLIENT_UPDATED' AND entity_id = ?", String.class, clientId)).isEqualTo("INACTIVE");
		List<String> actions = JsonPath.read(as(adminToken, get("/api/history/CLIENT_ENGAGEMENT/" + active)
			.param("size", "100")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
				"$.content[*].action");
		assertThat(actions).contains("RECORD_CREATED", "RECORD_DELETED", "RECORD_RESTORED", "ENGAGEMENT_DELETED");
	}

	@Test
	void consultantsImportFromCsvAfterAPreviewAndExportInTheSameColumns() throws Exception {
		Long existingEmployee = createEmployee("1");
		createEmployee("2");
		as(adminToken, post(API + "/consultants").contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":%d}".formatted(existingEmployee))).andExpect(status().isCreated());

		as(adminToken, get(API + "/imports/consultants/template")).andExpect(status().isOk())
			.andExpect(content().string(containsString("employee_id,experience_years")));
		MockMultipartFile file = new MockMultipartFile("file", "consultants.csv", "text/csv", """
				employee_id,experience_years,location,employment_status,skills,certifications
				%1$s,4.5,Pune,ACTIVE,SAP S/4HANA; Tosca,ISTQB Foundation
				%2$s,3,Chennai,,,
				BEN-%3$s-404,2,,,,
				%1$s,5,,,,
				""".formatted(code("2"), code("1"), nonce).getBytes(StandardCharsets.UTF_8));

		as(viewerToken, multipart(API + "/imports/consultants/preview").file(file)).andExpect(status().isForbidden());
		as(outsiderToken, multipart(API + "/imports/consultants/preview").file(file)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
		String preview = as(adminToken, multipart(API + "/imports/consultants/preview").file(file))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalRows").value(4))
			.andExpect(jsonPath("$.validCount").value(1))
			.andExpect(jsonPath("$.invalidCount").value(3))
			.andExpect(jsonPath("$.invalidRows[0].errors[0].message").value("This employee already is a consultant"))
			.andExpect(jsonPath("$.invalidRows[1].errors[0].message").value("No employee record has this ID"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		as(adminToken, multipart(API + "/imports/consultants/commit").file(file)
			.param("checksum", JsonPath.<String>read(preview, "$.checksum"))
			.param("skipInvalid", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));

		as(viewerToken, get(API + "/consultants").param("search", code("2"))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].skills[1]").value("Tosca"))
			.andExpect(jsonPath("$.content[0].certifications[0].name").value("ISTQB Foundation"))
			.andExpect(jsonPath("$.content[0].experienceYears").value(4.5));
		as(viewerToken, get(API + "/consultants/export").param("search", nonce)).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("consultants.csv")))
			.andExpect(content().string(containsString(code("2") + ",4.5,Pune,,ACTIVE,SAP S/4HANA; Tosca,ISTQB Foundation")))
			.andExpect(content().string(containsString(code("1") + ",,,,ACTIVE,,,")));
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private String code(String suffix) {
		return "BEN-" + nonce + "-" + suffix;
	}

	private Long createEmployee(String suffix) throws Exception {
		return id(as(adminToken, post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON).content("""
				{"employeeCode":"%s","firstName":"Bench","lastName":"%s","dateOfBirth":"1990-04-12",
				"designation":"Test Lead","reportingManager":"Rakesh","departmentId":%d}
				""".formatted(code(suffix), nonce, departmentId))).andExpect(status().isCreated()));
	}

	private Long createEngagement(Long clientId, String name, String start, String end, Long managerId)
			throws Exception {
		return id(as(adminToken, post(API + "/engagements").contentType(MediaType.APPLICATION_JSON)
			.content(engagement(clientId, name, start, end, managerId))).andExpect(status().isCreated()));
	}

	private static String engagement(Long clientId, String name, String start, String end, Long managerId) {
		return "{\"clientId\":%d,\"name\":\"%s\",\"startDate\":%s,\"endDate\":%s,\"accountManagerId\":%s}"
			.formatted(clientId, name, start, end, managerId);
	}

	private int version(String path) throws Exception {
		return JsonPath.read(as(adminToken, get(path)).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString(), "$.version");
	}

	private Long binItem(String type, Long entityId) throws Exception {
		String body = as(adminToken, get("/api/recycle-bin").param("entityType", type).param("size", "100"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> ids = JsonPath.read(body, "$.content[?(@.entityId == %d)].id".formatted(entityId));
		assertThat(ids).as(type + " #" + entityId + " in the bin").hasSize(1);
		return ids.get(0).longValue();
	}

	private static Long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private ResultActions as(String token, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String login(Long userId) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(users.email(userId), IntegrationUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

}
