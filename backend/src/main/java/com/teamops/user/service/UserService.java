package com.teamops.user.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.auth.repository.RefreshTokenRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.AuthenticatedUserFactory;
import com.teamops.common.security.PasswordPolicy;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.dto.ResetPasswordRequest;
import com.teamops.user.dto.UpdateUserAccessRequest;
import com.teamops.user.dto.UpdateUserRequest;
import com.teamops.user.dto.UserDetail;
import com.teamops.user.dto.UserListItem;
import com.teamops.user.dto.UserSearchCriteria;
import com.teamops.user.entity.Permission;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.PermissionRepository;
import com.teamops.user.repository.RoleRepository;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.repository.UserSpecifications;

import lombok.RequiredArgsConstructor;

/**
 * User administration. Enforces the rules that @PreAuthorize alone cannot express:
 * <ul>
 * <li>No privilege escalation: an actor can only grant roles/permissions they hold; only a Super Admin can grant
 * SUPER_ADMIN.</li>
 * <li>No self-lockout: an actor cannot disable themselves or change their own access.</li>
 * <li>At least one active Super Admin must always remain.</li>
 * </ul>
 * Methods taking an {@code actor} accept {@code null} for trusted system callers (admin bootstrap, dev seeder), which
 * skips the actor checks. Controllers always pass the authenticated user.
 */
@Service
@RequiredArgsConstructor
public class UserService {

	private static final int MAX_REPORTING_DEPTH = 100;

	private final UserRepository userRepository;

	private final DepartmentRepository departmentRepository;

	private final RoleRepository roleRepository;

	private final PermissionRepository permissionRepository;

	private final RefreshTokenRepository refreshTokenRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuthenticatedUserFactory authenticatedUserFactory;

	private final AuditService auditService;

	private final Clock clock;

	private final AppSettingsService settings;

	@Transactional(readOnly = true)
	public PageResponse<UserListItem> search(UserSearchCriteria criteria, Pageable pageable) {
		var spec = UserSpecifications.matches(criteria.search())
			.and(UserSpecifications.inDepartment(criteria.departmentId()))
			.and(UserSpecifications.withStatus(criteria.status()))
			.and(UserSpecifications.withRole(criteria.role()));
		return PageResponse.of(userRepository.findAll(spec, pageable).map(UserListItem::of));
	}

	@Transactional(readOnly = true)
	public UserDetail get(Long id) {
		return toDetail(loadWithAuthorities(id));
	}

	@Transactional
	public UserDetail create(CreateUserRequest request, AuthenticatedUser actor, ClientInfo client) {
		String email = normaliseEmail(request.email());
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw ApiException.conflict("EMAIL_IN_USE", "A user with this email already exists");
		}
		PasswordPolicy.validate(request.password());
		Set<Role> roles = resolveRoles(request.roles());
		Set<Permission> grants = resolvePermissions(request.permissions());
		assertCanGrant(actor, roles, grants);
		assertConsistent(roles, grants);

		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setFirstName(request.firstName().trim());
		user.setLastName(trimToEmpty(request.lastName()));
		user.setJobTitle(trimToNull(request.jobTitle()));
		user.setPhone(trimToNull(request.phone()));
		user.setLocation(trimToNull(request.location()));
		user.setWorkingHours(trimToNull(request.workingHours()));
		user.setDepartment(resolveActiveDepartment(request.departmentId()));
		user.setReportsTo(resolveReportsTo(request.reportsToId(), null));
		user.setWeeklyCapacityHours(
				request.weeklyCapacityHours() == null ? settings.defaultWeeklyCapacityHours() : request.weeklyCapacityHours());
		user.setRoles(new HashSet<>(roles));
		user.setDirectPermissions(new HashSet<>(grants));
		User saved = userRepository.save(user);

		auditService.record(AuditAction.USER_CREATED, actorId(actor), "USER", saved.getId(),
				Map.of("email", email, "departmentId", saved.getDepartment().getId(), "roles", roleCodes(roles),
						"permissions", permissionCodes(grants)),
				client);
		return toDetail(saved);
	}

	@Transactional
	public UserDetail update(Long id, UpdateUserRequest request, AuthenticatedUser actor, ClientInfo client) {
		User user = loadWithAuthorities(id);
		String email = normaliseEmail(request.email());
		if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
			throw ApiException.conflict("EMAIL_IN_USE", "A user with this email already exists");
		}
		Department department = Objects.equals(user.getDepartment().getId(), request.departmentId())
				? user.getDepartment() : resolveActiveDepartment(request.departmentId());
		User reportsTo = resolveReportsTo(request.reportsToId(), id);

		AuditChanges changes = new AuditChanges();
		changes.track("email", user.getEmail(), email);
		changes.track("firstName", user.getFirstName(), request.firstName().trim());
		changes.track("lastName", user.getLastName(), trimToEmpty(request.lastName()));
		changes.track("jobTitle", user.getJobTitle(), trimToNull(request.jobTitle()));
		changes.track("phone", user.getPhone(), trimToNull(request.phone()));
		changes.track("location", user.getLocation(), trimToNull(request.location()));
		changes.track("workingHours", user.getWorkingHours(), trimToNull(request.workingHours()));
		changes.track("departmentId", user.getDepartment().getId(), department.getId());
		changes.track("reportsToId", idOf(user.getReportsTo()), idOf(reportsTo));
		changes.track("weeklyCapacityHours", user.getWeeklyCapacityHours().stripTrailingZeros(),
				request.weeklyCapacityHours().stripTrailingZeros());

		user.setEmail(email);
		user.setFirstName(request.firstName().trim());
		user.setLastName(trimToEmpty(request.lastName()));
		user.setJobTitle(trimToNull(request.jobTitle()));
		user.setPhone(trimToNull(request.phone()));
		user.setLocation(trimToNull(request.location()));
		user.setWorkingHours(trimToNull(request.workingHours()));
		user.setDepartment(department);
		user.setReportsTo(reportsTo);
		user.setWeeklyCapacityHours(request.weeklyCapacityHours());

		if (!changes.isEmpty()) {
			auditService.record(AuditAction.USER_UPDATED, actorId(actor), "USER", id, changes.toDetails(), client);
		}
		return toDetail(user);
	}

	@Transactional
	public UserDetail updateAccess(Long id, UpdateUserAccessRequest request, AuthenticatedUser actor,
			ClientInfo client) {
		assertNotSelf(actor, id, "You cannot change your own roles or permissions");
		User user = loadWithAuthorities(id);
		Set<Role> roles = resolveRoles(request.roles());
		Set<Permission> grants = resolvePermissions(request.permissions());

		Set<Role> addedRoles = difference(roles, user.getRoles(), Role::getCode);
		Set<Permission> addedGrants = difference(grants, user.getDirectPermissions(), Permission::getCode);
		assertCanGrant(actor, addedRoles, addedGrants);
		assertConsistent(roles, grants);

		boolean losesSuperAdmin = hasRole(user.getRoles(), RoleCodes.SUPER_ADMIN)
				&& !hasRole(roles, RoleCodes.SUPER_ADMIN);
		if (losesSuperAdmin) {
			if (actor != null && !actor.isSuperAdmin()) {
				throw ApiException.forbidden("SUPER_ADMIN_PROTECTED", "Only a Super Admin can change Super Admin access");
			}
			assertNotLastActiveSuperAdmin(user);
		}

		Map<String, Object> before = Map.of("roles", roleCodes(user.getRoles()), "permissions",
				permissionCodes(user.getDirectPermissions()));
		user.setRoles(new HashSet<>(roles));
		user.setDirectPermissions(new HashSet<>(grants));
		Map<String, Object> after = Map.of("roles", roleCodes(roles), "permissions", permissionCodes(grants));

		if (!before.equals(after)) {
			auditService.record(AuditAction.USER_ACCESS_CHANGED, actorId(actor), "USER", id,
					Map.of("before", before, "after", after), client);
		}
		return toDetail(user);
	}

	/** Disabling revokes every refresh token; existing access tokens stop working on their next request. */
	@Transactional
	public UserDetail setStatus(Long id, UserStatus status, AuthenticatedUser actor, ClientInfo client) {
		if (status == UserStatus.DISABLED) {
			assertNotSelf(actor, id, "You cannot disable your own account");
		}
		User user = loadWithAuthorities(id);
		if (user.getStatus() == status) {
			return toDetail(user);
		}
		if (status == UserStatus.DISABLED) {
			if (hasRole(user.getRoles(), RoleCodes.SUPER_ADMIN)) {
				if (actor != null && !actor.isSuperAdmin()) {
					throw ApiException.forbidden("SUPER_ADMIN_PROTECTED", "Only a Super Admin can disable a Super Admin");
				}
				assertNotLastActiveSuperAdmin(user);
			}
			refreshTokenRepository.revokeAllActiveForUser(id, clock.instant());
		}
		user.setStatus(status);
		auditService.record(status == UserStatus.DISABLED ? AuditAction.USER_DISABLED : AuditAction.USER_ENABLED,
				actorId(actor), "USER", id, Map.of("email", user.getEmail()), client);
		return toDetail(user);
	}

	/** Sets a new password and signs the user out everywhere. */
	@Transactional
	public void resetPassword(Long id, ResetPasswordRequest request, AuthenticatedUser actor, ClientInfo client) {
		assertNotSelf(actor, id, "Use your own profile to change your password");
		User user = loadWithAuthorities(id);
		if (hasRole(user.getRoles(), RoleCodes.SUPER_ADMIN) && actor != null && !actor.isSuperAdmin()) {
			throw ApiException.forbidden("SUPER_ADMIN_PROTECTED", "Only a Super Admin can reset a Super Admin's password");
		}
		PasswordPolicy.validate(request.newPassword());
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		refreshTokenRepository.revokeAllActiveForUser(id, clock.instant());
		auditService.record(AuditAction.USER_PASSWORD_RESET, actorId(actor), "USER", id,
				Map.of("email", user.getEmail()), client);
	}

	private User loadWithAuthorities(Long id) {
		return userRepository.findWithAuthoritiesById(id)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
	}

	private UserDetail toDetail(User user) {
		return UserDetail.of(user, authenticatedUserFactory.from(user));
	}

	private Department resolveActiveDepartment(Long departmentId) {
		Department department = departmentRepository.findById(departmentId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		if (department.getStatus() != DepartmentStatus.ACTIVE) {
			throw ApiException.badRequest("DEPARTMENT_INACTIVE", "Users cannot be added to an inactive department");
		}
		return department;
	}

	/** Validates the manager exists and that the change does not create a reporting loop. */
	private User resolveReportsTo(Long reportsToId, Long userId) {
		if (reportsToId == null) {
			return null;
		}
		if (reportsToId.equals(userId)) {
			throw ApiException.badRequest("INVALID_REPORTS_TO", "A user cannot report to themselves");
		}
		User manager = userRepository.findById(reportsToId)
			.orElseThrow(() -> ApiException.badRequest("INVALID_REPORTS_TO", "Reporting manager not found"));
		User cursor = manager;
		for (int depth = 0; cursor != null && depth < MAX_REPORTING_DEPTH; depth++) {
			if (userId != null && userId.equals(cursor.getId())) {
				throw ApiException.badRequest("INVALID_REPORTS_TO", "This would create a reporting loop");
			}
			cursor = cursor.getReportsTo();
		}
		return manager;
	}

	private Set<Role> resolveRoles(Set<String> codes) {
		if (codes.isEmpty()) {
			throw ApiException.badRequest("ROLE_REQUIRED", "At least one role is required");
		}
		return resolveAll(roleRepository.findByCodeIn(codes), Role::getCode, codes, "UNKNOWN_ROLE");
	}

	private Set<Permission> resolvePermissions(Set<String> codes) {
		if (codes.isEmpty()) {
			return Set.of();
		}
		return resolveAll(permissionRepository.findByCodeIn(codes), Permission::getCode, codes, "UNKNOWN_PERMISSION");
	}

	private static <T> Set<T> resolveAll(List<T> found, Function<T, String> code, Set<String> requested,
			String errorCode) {
		Set<String> missing = new TreeSet<>(requested);
		found.forEach(item -> missing.remove(code.apply(item)));
		if (!missing.isEmpty()) {
			throw ApiException.badRequest(errorCode, "Unknown codes: " + missing);
		}
		return new HashSet<>(found);
	}

	/** An actor may only hand out permissions they hold themselves; SUPER_ADMIN only by a Super Admin. */
	private void assertCanGrant(AuthenticatedUser actor, Set<Role> roles, Set<Permission> grants) {
		if (actor == null) {
			return;
		}
		if (hasRole(roles, RoleCodes.SUPER_ADMIN) && !actor.isSuperAdmin()) {
			throw ApiException.forbidden("CANNOT_GRANT_SUPER_ADMIN", "Only a Super Admin can grant the Super Admin role");
		}
		Set<String> requested = Stream
			.concat(roles.stream().flatMap(role -> role.getPermissions().stream()), grants.stream())
			.map(Permission::getCode)
			.collect(Collectors.toCollection(TreeSet::new));
		requested.removeAll(actor.permissions());
		if (!requested.isEmpty()) {
			throw ApiException.forbidden("CANNOT_GRANT_PERMISSIONS",
					"You cannot grant permissions you do not hold: " + requested);
		}
	}

	private static void assertConsistent(Set<Role> roles, Set<Permission> grants) {
		MarketingPermissionRules.assertConsistent(
				Stream.concat(roles.stream().flatMap(role -> role.getPermissions().stream()), grants.stream())
					.map(Permission::getCode)
					.collect(Collectors.toSet()));
	}

	private void assertNotLastActiveSuperAdmin(User user) {
		if (user.isActive() && userRepository.countByRoleAndStatus(RoleCodes.SUPER_ADMIN, UserStatus.ACTIVE) <= 1) {
			throw ApiException.conflict("LAST_SUPER_ADMIN", "At least one active Super Admin is required");
		}
	}

	private static void assertNotSelf(AuthenticatedUser actor, Long targetId, String message) {
		if (actor != null && actor.id().equals(targetId)) {
			throw ApiException.forbidden("CANNOT_MODIFY_SELF", message);
		}
	}

	private static boolean hasRole(Set<Role> roles, String code) {
		return roles.stream().anyMatch(role -> code.equals(role.getCode()));
	}

	private static <T> Set<T> difference(Set<T> next, Set<T> current, Function<T, String> code) {
		Set<String> currentCodes = current.stream().map(code).collect(Collectors.toSet());
		return next.stream().filter(item -> !currentCodes.contains(code.apply(item))).collect(Collectors.toSet());
	}

	private static List<String> roleCodes(Set<Role> roles) {
		return roles.stream().map(Role::getCode).sorted().toList();
	}

	private static List<String> permissionCodes(Set<Permission> permissions) {
		return permissions.stream().map(Permission::getCode).sorted().toList();
	}

	private static Long actorId(AuthenticatedUser actor) {
		return actor == null ? null : actor.id();
	}

	private static Long idOf(User user) {
		return user == null ? null : user.getId();
	}

	private static String normaliseEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private static String trimToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

}
