package com.teamops.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Admin → Users → Import CSV end to end against MySQL: preview with valid and invalid rows, the all-or-nothing rule,
 * the one-time temporary passwords, the forced password change at first sign-in, and the audit trail. Runs in two
 * throwaway departments. Run with: mvnw verify -Pit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class UserImportFlowIT {

	private static final String HEADER = "first_name,last_name,email,phone,job_title,department_code,"
			+ "extra_department_codes,role,reports_to_email,location,time_zone,extra_permissions\n";

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

	private final List<Long> departments = new ArrayList<>();

	private String adminToken;

	private String primary;

	private String secondary;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId), IntegrationUsers.PASSWORD);
		primary = department();
		secondary = department();
	}

	@AfterEach
	void cleanUp() {
		users.deleteAll();
		departments.forEach(departmentRepository::deleteById);
	}

	@Test
	void importsValidRowsWithTemporaryPasswordsThatMustBeChanged() throws Exception {
		String nonce = UUID.randomUUID().toString().substring(0, 8);
		String lead = "lead-" + nonce + "@teamops.local";
		String member = "member-" + nonce + "@teamops.local";
		String marketer = "marketer-" + nonce + "@teamops.local";
		MockMultipartFile file = csv(HEADER
				+ "Lena,Lead,%s,+61400000000,\"Head - People, Digital\",%s,,DEPARTMENT_MANAGER,,Sydney,Australia/Sydney,\n"
					.formatted(lead, primary)
				+ "Max,Member,%s,,Developer,%s,%s,EMPLOYEE,%s,Chennai,Asia/Kolkata,\n".formatted(member, primary,
						secondary, lead)
				+ "Mia,Marketer,%s,,Marketer,%s,,EMPLOYEE,%s,Chennai,Asia/Kolkata,\"MARKETING_VIEW,LEAD_VIEW,LEAD_EDIT\"\n"
					.formatted(marketer, primary, lead)
				// Invalid: unknown department, role, permission and zone; a manager nobody has; an existing email.
				+ "Bad,Row,bad-%s@teamops.local,,,NOPE,,BOSS,ghost-%s@teamops.local,,Mars/Base,FLY\n".formatted(nonce,
						nonce)
				+ "Dup,Admin,%s,,,%s,,EMPLOYEE,,,,\n".formatted(users.email(users.create("IT", RoleCodes.EMPLOYEE)),
						primary)
				// Valid on its own, but reports to the invalid row above it.
				+ "Late,Report,late-%s@teamops.local,,,%s,,EMPLOYEE,bad-%s@teamops.local,,,\n".formatted(nonce,
						primary, nonce));

		String preview = as(adminToken, multipart("/api/users/import/preview").file(file)).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalRows").value(6))
			.andExpect(jsonPath("$.validCount").value(3))
			.andExpect(jsonPath("$.invalidCount").value(3))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Map<String, Object>> badErrors = JsonPath.read(preview, "$.invalidRows[0].errors");
		assertThat(badErrors).extracting(e -> e.get("column"))
			.contains("department_code", "role", "extra_permissions", "reports_to_email", "time_zone");
		assertThat(preview).contains("A user with this email already exists")
			.contains("Reports to a person on an earlier row that has errors");
		assertThat(userRepository.findByEmailIgnoreCase(lead)).as("the preview saves nothing").isEmpty();

		String checksum = JsonPath.read(preview, "$.checksum");
		as(adminToken, multipart("/api/users/import/commit").file(file).param("checksum", checksum))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"));
		String result = as(adminToken, multipart("/api/users/import/commit").file(file).param("checksum", checksum)
			.param("skipInvalid", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(3))
			.andExpect(jsonPath("$.skipped").value(3))
			.andReturn()
			.getResponse()
			.getContentAsString();

		User leadUser = track(lead);
		User memberUser = track(member);
		track(marketer);
		assertThat(memberUser.getReportsTo().getId()).isEqualTo(leadUser.getId());
		assertThat(memberUser.getTimeZone()).isEqualTo("Asia/Kolkata");
		assertThat(memberUser.isMustChangePassword()).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from department_members m join departments d "
				+ "on d.id = m.department_id where m.user_id = ? and d.code = ?", Integer.class, memberUser.getId(),
				secondary)).isEqualTo(1);

		// The result file lists each new person once, with a temporary password that works exactly once.
		String content = JsonPath.read(result, "$.resultFile.content");
		assertThat(content).as("UTF-8 byte order mark, for Excel").startsWith("﻿");
		List<String> lines = content.substring(1).lines().toList();
		assertThat(lines).hasSize(4);
		assertThat(lines.get(0)).isEqualTo("email,name,temporary_password");
		String[] memberLine = lines.stream().filter(l -> l.startsWith(member)).findFirst().orElseThrow().split(",");
		String temporary = memberLine[2];
		assertThat(memberLine[1]).isEqualTo("Max Member");

		String memberToken = loginBody(member, temporary);
		assertThat((Boolean) JsonPath.read(memberToken, "$.user.mustChangePassword")).isTrue();
		String token = JsonPath.read(memberToken, "$.accessToken");
		as(token, get("/api/tasks")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
		as(token, get("/api/auth/me")).andExpect(status().isOk());
		as(token, post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"%s\",\"newPassword\":\"short1\"}".formatted(temporary)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
		String changed = as(token, post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"%s\",\"newPassword\":\"MyOwnPass2026\"}".formatted(temporary)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.mustChangePassword").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		as(JsonPath.read(changed, "$.accessToken"), get("/api/tasks")).andExpect(status().isOk());
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member, temporary)))
			.andExpect(status().isUnauthorized());

		// Audited: the import, each new user, and the password change. Passwords never reach the audit log.
		assertThat(count("select count(*) from audit_logs where action = 'USERS_IMPORTED' and details like ?",
				"%" + lead + "%")).isEqualTo(1);
		assertThat(count("select count(*) from audit_logs where action = 'CSV_IMPORTED' and details like ?",
				"%" + checksum + "%")).isEqualTo(1);
		assertThat(count("select count(*) from audit_logs where action = 'USER_CREATED' and entity_id = ?",
				memberUser.getId())).isEqualTo(1);
		assertThat(count("select count(*) from audit_logs where action = 'PASSWORD_CHANGED' and entity_id = ?",
				memberUser.getId())).isEqualTo(1);
		assertThat(count("select count(*) from audit_logs where details like ?", "%" + temporary + "%")).isZero();
	}

	@Test
	void onlyUserAdministratorsCanImportAndNobodyGainsMoreThanTheyHold() throws Exception {
		Long employeeId = users.create("IT", RoleCodes.EMPLOYEE);
		String employeeToken = login(users.email(employeeId), IntegrationUsers.PASSWORD);
		MockMultipartFile file = csv(HEADER + "A,B,x-%s@teamops.local,,,%s,,EMPLOYEE,,,,\n"
			.formatted(UUID.randomUUID(), primary));
		as(employeeToken, multipart("/api/users/import/preview").file(file)).andExpect(status().isForbidden());

		// A user administrator without Super Admin cannot create a Super Admin or grant what they lack.
		Long hrAdmin = users.create("IT", RoleCodes.EMPLOYEE, "USER_MANAGE");
		String hrToken = login(users.email(hrAdmin), IntegrationUsers.PASSWORD);
		String escalation = as(hrToken, multipart("/api/users/import/preview").file(csv(HEADER
				+ "S,A,sa-%s@teamops.local,,,%s,,SUPER_ADMIN,,,,\n".formatted(UUID.randomUUID(), primary)
				+ "M,V,mv-%s@teamops.local,,,%s,,EMPLOYEE,,,,AUDIT_VIEW\n".formatted(UUID.randomUUID(), primary))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.validCount").value(0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(escalation).contains("Only a Super Admin can grant the Super Admin role")
			.contains("You cannot grant permissions you do not hold");

		// The marketing import endpoints do not serve the users import.
		as(adminToken, multipart("/api/marketing/imports/users/preview").file(file)).andExpect(status().isNotFound());
	}

	private User track(String email) {
		User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		users.track(user.getId(), email);
		return userRepository.findWithAuthoritiesById(user.getId()).orElseThrow();
	}

	private String department() throws Exception {
		String code = "IMP_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String body = as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Import %s\",\"code\":\"%s\"}".formatted(code, code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		departments.add(((Number) JsonPath.read(body, "$.id")).longValue());
		return code;
	}

	private static MockMultipartFile csv(String text) {
		return new MockMultipartFile("file", "team.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}

	private ResultActions as(String token, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String loginBody(String email, String password) throws Exception {
		return mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private String login(String email, String password) throws Exception {
		return JsonPath.read(loginBody(email, password), "$.accessToken");
	}

}
