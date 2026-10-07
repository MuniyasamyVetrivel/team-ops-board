package com.teamops.user.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.entity.Permission;
import com.teamops.user.entity.Role;
import com.teamops.user.entity.User;
import com.teamops.user.repository.PermissionRepository;
import com.teamops.user.repository.RoleRepository;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Creates users with roles and direct permission grants. Used by the admin bootstrap and the dev seeder. */
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

	static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;

	private final DepartmentRepository departmentRepository;

	private final RoleRepository roleRepository;

	private final PermissionRepository permissionRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuditService auditService;

	@Transactional
	public User createUser(NewUser request, Long actorId) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw ApiException.conflict("EMAIL_IN_USE", "A user with this email already exists");
		}
		if (request.rawPassword() == null || request.rawPassword().length() < MIN_PASSWORD_LENGTH) {
			throw ApiException.badRequest("WEAK_PASSWORD",
					"Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
		}
		if (request.roleCodes().isEmpty()) {
			throw ApiException.badRequest("ROLE_REQUIRED", "At least one role is required");
		}
		Department department = departmentRepository.findByCode(request.departmentCode())
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT",
					"Unknown department: " + request.departmentCode()));

		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.rawPassword()));
		user.setFirstName(request.firstName().trim());
		user.setLastName(request.lastName() == null ? "" : request.lastName().trim());
		user.setJobTitle(request.jobTitle());
		user.setDepartment(department);
		user.setRoles(new HashSet<>(findAll(roleRepository.findByCodeIn(request.roleCodes()), Role::getCode,
				request.roleCodes(), "UNKNOWN_ROLE")));
		user.setDirectPermissions(new HashSet<>(findAll(permissionRepository.findByCodeIn(request.permissionCodes()),
				Permission::getCode, request.permissionCodes(), "UNKNOWN_PERMISSION")));
		User saved = userRepository.save(user);

		auditService.record(AuditAction.USER_CREATED, actorId, "USER", saved.getId(),
				Map.of("email", email, "roles", request.roleCodes()), ClientInfo.unknown());
		return saved;
	}

	@Transactional
	public void assignDepartmentManager(String departmentCode, Long userId) {
		Department department = departmentRepository.findByCode(departmentCode)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Unknown department: " + departmentCode));
		department.setManager(userRepository.getReferenceById(userId));
	}

	@Transactional
	public void setReportsTo(Long userId, Long managerId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
		user.setReportsTo(userRepository.getReferenceById(managerId));
	}

	private static <T> List<T> findAll(List<T> found, java.util.function.Function<T, String> code,
			Set<String> requested, String errorCode) {
		Set<String> foundCodes = found.stream().map(code).collect(Collectors.toSet());
		Set<String> missing = new HashSet<>(requested);
		missing.removeAll(foundCodes);
		if (!missing.isEmpty()) {
			throw ApiException.badRequest(errorCode, "Unknown codes: " + missing);
		}
		return found;
	}

}
