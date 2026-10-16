package com.teamops.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.teamops.common.exception.ApiException;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.BreakGlassService;
import com.teamops.user.service.BreakGlassService.Operator;
import com.teamops.user.service.BreakGlassService.Result;
import com.teamops.user.service.UserService;

/**
 * Break-glass recovery against MySQL: a new Super Admin, or a Super Admin's password reset (re-enabling a disabled
 * account), always with a password to change at the next sign-in and an audit entry naming the server user. Only
 * Super Admin accounts can be reset this way. Throwaway users only; nothing is left behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.scheduling.enabled=false" })
class BreakGlassIT {

	private static final String PASSWORD = "Recovery2026pass";

	private static final Operator OPERATOR = new Operator("it-ops", "it-server");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private BreakGlassService breakGlass;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	@BeforeEach
	void setUp() {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
	}

	@AfterEach
	void cleanUp() {
		users.deleteAll();
	}

	@Test
	void createsANewSuperAdminWhoMustChangeThePassword() throws Exception {
		String email = "break.glass." + System.nanoTime() + "@teamops.local";
		Result result = breakGlass.createSuperAdmin(IntegrationUsers.employeeId(), email, "Break", "Glass", "IT", PASSWORD,
				OPERATOR);
		users.track(result.userId(), email);

		login(email, PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.roles[0]").value(RoleCodes.SUPER_ADMIN))
			.andExpect(jsonPath("$.user.mustChangePassword").value(true));
		assertThat(audits("BREAK_GLASS_USED", result.userId(), "create-super-admin")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_CREATED' AND entity_id = ?"
				+ " AND user_agent = 'break-glass CLI (it-ops@it-server)'", Long.class, result.userId())).isEqualTo(1);

		// The same email again is refused: an existing Super Admin is reset instead.
		assertThatThrownBy(() -> breakGlass.createSuperAdmin(IntegrationUsers.employeeId(), email, "Again", "", "IT",
				PASSWORD, OPERATOR))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("EMAIL_IN_USE"));
		// Super Admin passwords need 12+ characters.
		assertThatThrownBy(() -> breakGlass.createSuperAdmin(IntegrationUsers.employeeId(), "x" + email, "X", "", "IT",
				"Short1pass", OPERATOR))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("WEAK_PASSWORD"));
	}

	@Test
	void resetsALockedOutSuperAdminAndReEnablesTheAccount() throws Exception {
		Long adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		users.passwordChanged(adminId);
		Long otherAdminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		userService.setStatus(adminId, UserStatus.DISABLED, null, null);
		// Disabled: refused even with the right password.
		login(users.email(adminId), IntegrationUsers.PASSWORD).andExpect(status().isForbidden());

		Result result = breakGlass.resetSuperAdminPassword(users.email(adminId).toUpperCase(), PASSWORD, OPERATOR);

		assertThat(result.reEnabled()).isTrue();
		login(users.email(adminId), IntegrationUsers.PASSWORD).andExpect(status().isUnauthorized());
		login(users.email(adminId), PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.mustChangePassword").value(true));
		assertThat(audits("BREAK_GLASS_USED", adminId, "reset-password")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND action IN "
				+ "('USER_PASSWORD_RESET', 'USER_ENABLED')", Long.class, adminId)).isEqualTo(2);
		assertThat(otherAdminId).isNotNull();
	}

	@Test
	void onlySuperAdminPasswordsCanBeReset() {
		Long employeeId = users.create("IT", RoleCodes.EMPLOYEE);
		assertThatThrownBy(() -> breakGlass.resetSuperAdminPassword(users.email(employeeId), PASSWORD, OPERATOR))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("NOT_SUPER_ADMIN"));
		assertThatThrownBy(() -> breakGlass.resetSuperAdminPassword("nobody." + System.nanoTime() + "@teamops.local",
				PASSWORD, OPERATOR))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND"));
		assertThat(audits("BREAK_GLASS_USED", employeeId, "reset-password")).isZero();
	}

	private long audits(String action, Long userId, String command) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = ? AND entity_type = 'USER' AND entity_id = ? AND actor_id IS NULL
				  AND JSON_UNQUOTE(JSON_EXTRACT(details, '$.command')) = ?
				  AND JSON_UNQUOTE(JSON_EXTRACT(details, '$.osUser')) = 'it-ops'
				  AND JSON_UNQUOTE(JSON_EXTRACT(details, '$.host')) = 'it-server'""", Long.class, action, userId,
				command);
	}

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

}
