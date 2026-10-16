package com.teamops.user.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.PasswordPolicy;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.dto.ResetPasswordRequest;
import com.teamops.user.dto.UserDetail;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Break-glass recovery when nobody can sign in as Super Admin (docs/backup-and-restore.md). Used only by the
 * command-line tool on the server ({@link BreakGlassRunner}); there is no HTTP endpoint. It creates a new Super Admin
 * or resets an existing Super Admin's password (re-enabling a disabled account), always with a password that must be
 * changed at the next sign-in, and goes through {@link UserService} so the usual audit entries are written too. Every
 * use is recorded as BREAK_GLASS_USED with the operating system user and host.
 */
@Service
@RequiredArgsConstructor
public class BreakGlassService {

	/** Super Admin passwords are longer than the general minimum, as for the bootstrap admin. */
	public static final int MIN_PASSWORD_LENGTH = BootstrapAdminInitializer.MIN_ADMIN_PASSWORD_LENGTH;

	private final UserService userService;

	private final UserRepository userRepository;

	private final DepartmentRepository departmentRepository;

	private final AuditService auditService;

	/** Who ran the tool, from the operating system (the app has no signed-in user here). */
	public record Operator(String osUser, String host) {

		ClientInfo client() {
			String agent = "break-glass CLI (" + osUser + "@" + host + ")";
			return new ClientInfo(null, agent.length() > 512 ? agent.substring(0, 512) : agent);
		}

	}

	public record Result(Long userId, String email, boolean reEnabled) {

	}

	@Transactional
	public Result createSuperAdmin(String email, String firstName, String lastName, String departmentCode,
			String password, Operator operator) {
		validatePassword(password);
		if (!StringUtils.hasText(email) || !StringUtils.hasText(firstName)) {
			throw ApiException.badRequest("BREAK_GLASS_INPUT", "Email and first name are required");
		}
		String code = StringUtils.hasText(departmentCode) ? departmentCode.trim() : "IT";
		Long departmentId = departmentRepository.findByCode(code)
			.orElseThrow(() -> ApiException.badRequest("DEPARTMENT_NOT_FOUND", "No department with code " + code))
			.getId();
		if (userRepository.findByEmailIgnoreCase(email.trim()).isPresent()) {
			throw ApiException.conflict("EMAIL_IN_USE",
					"A user with this email already exists; use reset-password for an existing Super Admin");
		}
		UserDetail created = userService.create(new CreateUserRequest(email.trim(), password, firstName.trim(),
				lastName == null ? "" : lastName.trim(), null, null, null, null, null, departmentId, null, null,
				Set.of(RoleCodes.SUPER_ADMIN), Set.of()), null, operator.client());
		audit("create-super-admin", created.id(), created.email(), false, operator);
		return new Result(created.id(), created.email(), false);
	}

	/** Only for Super Admins: anyone else is helped by a Super Admin in the app, not from the server. */
	@Transactional
	public Result resetSuperAdminPassword(String email, String password, Operator operator) {
		validatePassword(password);
		User user = userRepository.findWithAuthoritiesByEmailIgnoreCase(email == null ? "" : email.trim())
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No user with this email"));
		boolean superAdmin = user.getRoles().stream().map(Role::getCode).anyMatch(RoleCodes.SUPER_ADMIN::equals);
		if (!superAdmin) {
			throw ApiException.forbidden("NOT_SUPER_ADMIN",
					"Break-glass only resets Super Admin passwords; a Super Admin can reset anyone else's in the app");
		}
		userService.resetPassword(user.getId(), new ResetPasswordRequest(password), null, operator.client());
		boolean reEnable = user.getStatus() == UserStatus.DISABLED;
		if (reEnable) {
			userService.setStatus(user.getId(), UserStatus.ACTIVE, null, operator.client());
		}
		audit("reset-password", user.getId(), user.getEmail(), reEnable, operator);
		return new Result(user.getId(), user.getEmail(), reEnable);
	}

	static void validatePassword(String password) {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw ApiException.badRequest("WEAK_PASSWORD",
					"A Super Admin password needs at least " + MIN_PASSWORD_LENGTH + " characters");
		}
		PasswordPolicy.validate(password);
	}

	private void audit(String command, Long userId, String email, boolean reEnabled, Operator operator) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("command", command);
		details.put("email", email);
		details.put("reEnabled", reEnabled);
		details.put("osUser", operator.osUser());
		details.put("host", operator.host());
		auditService.record(AuditAction.BREAK_GLASS_USED, null, "USER", userId, details, operator.client());
	}

}
