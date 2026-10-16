package com.teamops.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * Phase X4 against MySQL: every user has a unique employee ID, set at creation and changed only by a Super Admin,
 * visible to everyone signed in (profile, team directory, search) and linking the user to the Employee record with
 * the same code. Profile photos: anyone changes their own, only the Super Admin someone else's, everyone sees them;
 * the server stores a square JPEG and deletes the old file. Throwaway users and employees only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads",
		"app.scheduling.enabled=false" })
class EmployeeIdAndPhotoIT {

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

	private Long employeeId;

	private String adminToken;

	private String userAdminToken;

	private String employeeToken;

	private String colleagueToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		nonce = Long.toHexString(System.nanoTime()).toUpperCase();
		departmentId = departmentRepository.findByCode("IT").orElseThrow().getId();
		adminToken = login(users.email(users.create("IT", RoleCodes.SUPER_ADMIN)));
		userAdminToken = login(users.email(users.create("IT", RoleCodes.EMPLOYEE, "USER_MANAGE")));
		employeeId = users.create("IT", RoleCodes.EMPLOYEE);
		employeeToken = login(users.email(employeeId));
		colleagueToken = login(users.email(users.create("IT", RoleCodes.EMPLOYEE)));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM employees WHERE employee_code LIKE ?", "X4-" + nonce + "%");
		users.deleteAll();
	}

	@Test
	void employeeIdsAreRequiredUniqueAndChangedOnlyByTheSuperAdmin() throws Exception {
		String code = "X4-" + nonce + "-1";
		// Whoever may create users sets the ID; it is required and unique (ignoring case).
		as(userAdminToken, post("/api/users").contentType(MediaType.APPLICATION_JSON).content(newUser(null, "a")))
			.andExpect(status().isBadRequest());
		Long id = id(as(userAdminToken, post("/api/users").contentType(MediaType.APPLICATION_JSON)
			.content(newUser(code, "a"))).andExpect(status().isCreated()).andExpect(jsonPath("$.employeeId").value(code)));
		users.track(id, "x4-a-" + nonce + "@teamops.local");
		as(adminToken, post("/api/users").contentType(MediaType.APPLICATION_JSON)
			.content(newUser(code.toLowerCase(), "b"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMPLOYEE_ID_IN_USE"));

		// Changing it: 403 for a user administrator, fine for the Super Admin.
		as(userAdminToken, put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON).content(update(code + "-NEW")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EMPLOYEE_ID_SUPER_ADMIN_ONLY"));
		as(userAdminToken, put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON).content(update(code)))
			.andExpect(status().isOk());
		as(adminToken, put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON).content(update(code + "-NEW")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.employeeId").value(code + "-NEW"));

		// Everyone signed in sees it: their own profile, the team directory, search (by ID too).
		String mine = userRepository.findById(employeeId).orElseThrow().getEmployeeId();
		as(employeeToken, get("/api/auth/me")).andExpect(jsonPath("$.employeeId").value(mine));
		as(colleagueToken, get("/api/team").param("search", code + "-NEW"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].employeeId").value(code + "-NEW"));
		as(colleagueToken, get("/api/search").param("q", code + "-NEW"))
			.andExpect(jsonPath("$.groups[?(@.group == 'EMPLOYEES')].hits[0].code").value(code + "-NEW"));

		// Users from before Phase X4 without one are listed for the Super Admin.
		jdbc.update("UPDATE users SET employee_id = NULL WHERE id = ?", id);
		List<Integer> missing = JsonPath.read(as(adminToken, get("/api/users").param("missingEmployeeId", "true")
			.param("size", "100")).andReturn().getResponse().getContentAsString(), "$.content[*].id");
		assertThat(missing).contains(id.intValue()).doesNotContain(employeeId.intValue());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_UPDATED' AND entity_id = ? "
				+ "AND JSON_EXTRACT(details, '$.changes.employeeId') IS NOT NULL", Long.class, id)).isEqualTo(1);
	}

	@Test
	void usersAndEmployeeRecordsLinkByEmployeeId() throws Exception {
		String mine = userRepository.findById(employeeId).orElseThrow().getEmployeeId();
		String code = "X4-" + nonce + "-2";
		// An Employee saved with a user's employee ID is linked to that user.
		jdbc.update("UPDATE users SET employee_id = ? WHERE id = ?", code, employeeId);
		Long recordId = id(as(adminToken, post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON)
			.content(employeeRecord(code))).andExpect(status().isCreated()).andExpect(jsonPath("$.user.id").value(employeeId)));

		// The Super Admin changes the user's employee ID: the link follows the ID.
		as(adminToken, put("/api/users/" + employeeId).contentType(MediaType.APPLICATION_JSON).content(update(mine)))
			.andExpect(status().isOk());
		as(adminToken, get("/api/celebrations/employees/" + recordId)).andExpect(jsonPath("$.user").isEmpty());

		// A user created with an Employee's code is linked straight away.
		String later = "X4-" + nonce + "-3";
		Long otherRecord = id(as(adminToken, post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON)
			.content(employeeRecord(later))).andExpect(status().isCreated()).andExpect(jsonPath("$.user").isEmpty()));
		Long newUser = id(as(adminToken, post("/api/users").contentType(MediaType.APPLICATION_JSON)
			.content(newUser(later, "c"))).andExpect(status().isCreated()));
		users.track(newUser, "x4-c-" + nonce + "@teamops.local");
		as(adminToken, get("/api/celebrations/employees/" + otherRecord)).andExpect(jsonPath("$.user.id").value(newUser));

		// Linking by hand to a user with another employee ID is refused.
		as(adminToken, post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON)
			.content(employeeRecord("X4-" + nonce + "-4").replace("}", ",\"userId\":" + employeeId + "}")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMPLOYEE_ID_MISMATCH"));
	}

	@Test
	void anyoneChangesTheirOwnPhotoOnlyTheSuperAdminSomeoneElsesAndEveryoneSeesThem() throws Exception {
		Number first = JsonPath.read(as(employeeToken, multipart(HttpMethod.PUT, "/api/users/me/photo").file(png(900, 600)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.photoVersion");

		byte[] served = as(colleagueToken, get("/api/users/" + employeeId + "/photo")).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(served));
		assertThat(image.getWidth()).isEqualTo(512);
		assertThat(image.getHeight()).isEqualTo(512);
		as(colleagueToken, get("/api/team/" + employeeId)).andExpect(jsonPath("$.member.photoVersion").value(first.longValue()));

		as(colleagueToken, multipart(HttpMethod.PUT, "/api/users/" + employeeId + "/photo").file(png(300, 300)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PHOTO_FORBIDDEN"));
		as(userAdminToken, delete("/api/users/" + employeeId + "/photo")).andExpect(status().isForbidden());

		Number second = JsonPath.read(as(adminToken, multipart(HttpMethod.PUT, "/api/users/" + employeeId + "/photo")
			.file(png(300, 300))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.photoVersion");
		assertThat(second.longValue()).isNotEqualTo(first.longValue());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Long.class, first.longValue())).isZero();

		// A file that is not an image is refused whatever its name.
		as(employeeToken, multipart(HttpMethod.PUT, "/api/users/me/photo")
			.file(new MockMultipartFile("file", "me.png", "image/png", "not an image".getBytes())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PHOTO_TYPE_NOT_ALLOWED"));

		as(employeeToken, delete("/api/users/me/photo")).andExpect(status().isOk())
			.andExpect(jsonPath("$.photoVersion").isEmpty());
		as(colleagueToken, get("/api/users/" + employeeId + "/photo")).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Long.class, second.longValue())).isZero();
		assertThat(jdbc.queryForList("SELECT JSON_UNQUOTE(JSON_EXTRACT(details, '$.change')) FROM audit_logs "
				+ "WHERE action = 'USER_PHOTO_CHANGED' AND entity_id = ? ORDER BY id", String.class, employeeId))
			.containsExactly("added", "replaced", "removed");
	}

	@Test
	void aLinkedEmployeeRecordShowsTheUsersPhoto() throws Exception {
		String code = "X4-" + nonce + "-5";
		jdbc.update("UPDATE users SET employee_id = ? WHERE id = ?", code, employeeId);
		Long recordId = id(as(adminToken, post("/api/celebrations/employees").contentType(MediaType.APPLICATION_JSON)
			.content(employeeRecord(code))).andExpect(jsonPath("$.hasPhoto").value(false)));
		as(employeeToken, multipart(HttpMethod.PUT, "/api/users/me/photo").file(png(200, 200))).andExpect(status().isOk());

		as(adminToken, get("/api/celebrations/employees/" + recordId)).andExpect(jsonPath("$.hasPhoto").value(true));
		as(adminToken, get("/api/celebrations/employees/" + recordId + "/photo")).andExpect(status().isOk());
	}

	private String newUser(String code, String tag) {
		return """
				{%s"email":"x4-%s-%s@teamops.local","password":"Welcome123","firstName":"Phase","lastName":"Four",
				 "departmentId":%d,"roles":["EMPLOYEE"],"permissions":[]}
				""".formatted(code == null ? "" : "\"employeeId\":\"" + code + "\",", tag, nonce, departmentId);
	}

	private String update(String code) {
		return """
				{"email":"%s","firstName":"Phase","lastName":"Four","departmentId":%d,"weeklyCapacityHours":40,
				 "employeeId":"%s"}
				""".formatted("x4-upd-" + Long.toHexString(System.nanoTime()) + "@teamops.local", departmentId, code);
	}

	private String employeeRecord(String code) {
		return """
				{"employeeCode":"%s","firstName":"Phase","lastName":"Four","dateOfBirth":"1990-04-12",
				 "designation":"Engineer","reportingManager":"Rakesh","departmentId":%d}
				""".formatted(code, departmentId);
	}

	private static MockMultipartFile png(int width, int height) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
		return new MockMultipartFile("file", "me.png", "image/png", out.toByteArray());
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
