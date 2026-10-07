package com.teamops.department.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.dto.CreateDepartmentRequest;
import com.teamops.department.dto.DepartmentDetail;
import com.teamops.department.dto.DepartmentListItem;
import com.teamops.department.dto.DepartmentMemberItem;
import com.teamops.department.dto.UpdateDepartmentRequest;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentMember;
import com.teamops.department.entity.DepartmentMemberId;
import com.teamops.department.entity.DepartmentMemberRole;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentCount;
import com.teamops.department.repository.DepartmentMemberRepository;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Departments and their membership. Creating and editing departments needs DEPARTMENT_MANAGE. Secondary members can
 * also be managed by the department's own managers (see {@link AccessScopeService}), but only DEPARTMENT_MANAGE may
 * hand out MANAGER memberships, because those widen a manager's scope.
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

	private final DepartmentRepository departmentRepository;

	private final DepartmentMemberRepository departmentMemberRepository;

	private final UserRepository userRepository;

	private final AccessScopeService accessScopeService;

	private final AuditService auditService;

	@Transactional(readOnly = true)
	public List<DepartmentListItem> list() {
		Map<Long, Long> primary = toMap(userRepository.countByDepartment(UserStatus.ACTIVE));
		Map<Long, Long> secondary = toMap(departmentMemberRepository.countActiveByDepartment());
		return departmentRepository.findAllByOrderByNameAsc()
			.stream()
			.map(d -> DepartmentListItem.of(d, primary.getOrDefault(d.getId(), 0L),
					secondary.getOrDefault(d.getId(), 0L)))
			.toList();
	}

	@Transactional(readOnly = true)
	public DepartmentDetail get(Long id, AuthenticatedUser viewer) {
		Department department = load(id);
		List<DepartmentMemberItem> members = new ArrayList<>();
		userRepository.findByDepartmentIdOrderByFirstNameAscLastNameAsc(id)
			.forEach(user -> members.add(DepartmentMemberItem.of(user, DepartmentMemberItem.PRIMARY)));
		departmentMemberRepository.findByIdDepartmentId(id)
			.stream()
			.sorted(Comparator.comparing((DepartmentMember m) -> m.getUser().getFullName()))
			.forEach(m -> members.add(DepartmentMemberItem.of(m.getUser(), m.getMemberRole().name())));
		return DepartmentDetail.of(department, members, viewer.hasPermission(PermissionCodes.DEPARTMENT_MANAGE),
				accessScopeService.canManageDepartment(viewer, id));
	}

	@Transactional
	public DepartmentDetail create(CreateDepartmentRequest request, AuthenticatedUser actor, ClientInfo client) {
		String code = request.code().trim().toUpperCase(Locale.ROOT);
		String name = request.name().trim();
		if (departmentRepository.existsByCodeIgnoreCase(code)) {
			throw ApiException.conflict("DEPARTMENT_CODE_IN_USE", "A department with this code already exists");
		}
		if (departmentRepository.existsByNameIgnoreCase(name)) {
			throw ApiException.conflict("DEPARTMENT_NAME_IN_USE", "A department with this name already exists");
		}
		Department department = new Department();
		department.setCode(code);
		department.setName(name);
		department.setDescription(trimToNull(request.description()));
		department.setManager(resolveManager(request.managerId()));
		Department saved = departmentRepository.save(department);
		auditService.record(AuditAction.DEPARTMENT_CREATED, actorId(actor), "DEPARTMENT", saved.getId(),
				Map.of("code", code, "name", name), client);
		return get(saved.getId(), actor);
	}

	@Transactional
	public DepartmentDetail update(Long id, UpdateDepartmentRequest request, AuthenticatedUser actor,
			ClientInfo client) {
		Department department = load(id);
		String name = request.name().trim();
		if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw ApiException.conflict("DEPARTMENT_NAME_IN_USE", "A department with this name already exists");
		}
		if (request.status() == DepartmentStatus.INACTIVE && department.getStatus() == DepartmentStatus.ACTIVE
				&& userRepository.countByDepartmentIdAndStatus(id, UserStatus.ACTIVE) > 0) {
			throw ApiException.conflict("DEPARTMENT_HAS_ACTIVE_USERS",
					"Move or disable this department's active users before deactivating it");
		}
		User manager = Objects.equals(idOf(department.getManager()), request.managerId()) ? department.getManager()
				: resolveManager(request.managerId());

		AuditChanges changes = new AuditChanges().track("name", department.getName(), name)
			.track("description", department.getDescription(), trimToNull(request.description()))
			.track("managerId", idOf(department.getManager()), idOf(manager))
			.track("status", department.getStatus(), request.status());

		department.setName(name);
		department.setDescription(trimToNull(request.description()));
		department.setManager(manager);
		department.setStatus(request.status());
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.DEPARTMENT_UPDATED, actorId(actor), "DEPARTMENT", id,
					changes.toDetails(), client);
		}
		return get(id, actor);
	}

	/** Adds or updates a secondary membership. */
	@Transactional
	public DepartmentDetail upsertMember(Long departmentId, Long userId, DepartmentMemberRole role,
			AuthenticatedUser actor, ClientInfo client) {
		Department department = load(departmentId);
		assertCanManageMembers(actor, departmentId);
		if (role == DepartmentMemberRole.MANAGER && !actor.hasPermission(PermissionCodes.DEPARTMENT_MANAGE)) {
			throw ApiException.forbidden("CANNOT_ASSIGN_MANAGER",
					"Only administrators with Department Manage can assign manager memberships");
		}
		User user = userRepository.findWithDepartmentById(userId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "User not found"));
		if (!user.isActive()) {
			throw ApiException.badRequest("USER_DISABLED", "Disabled users cannot be added to a department");
		}
		if (user.getDepartment().getId().equals(departmentId)) {
			throw ApiException.badRequest("ALREADY_PRIMARY_MEMBER", "This is already the user's primary department");
		}
		DepartmentMember member = departmentMemberRepository.findById(new DepartmentMemberId(departmentId, userId))
			.orElseGet(() -> DepartmentMember.of(department, user, role));
		if (member.getMemberRole() == DepartmentMemberRole.MANAGER && role != DepartmentMemberRole.MANAGER
				&& !actor.hasPermission(PermissionCodes.DEPARTMENT_MANAGE)) {
			throw ApiException.forbidden("CANNOT_ASSIGN_MANAGER", "Only administrators can change a manager membership");
		}
		member.setMemberRole(role);
		departmentMemberRepository.save(member);
		auditService.record(AuditAction.DEPARTMENT_MEMBER_ADDED, actor.id(), "DEPARTMENT", departmentId,
				Map.of("userId", userId, "role", role), client);
		return get(departmentId, actor);
	}

	@Transactional
	public DepartmentDetail removeMember(Long departmentId, Long userId, AuthenticatedUser actor, ClientInfo client) {
		load(departmentId);
		assertCanManageMembers(actor, departmentId);
		DepartmentMember member = departmentMemberRepository.findById(new DepartmentMemberId(departmentId, userId))
			.orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "This user is not a secondary member"));
		if (member.getMemberRole() == DepartmentMemberRole.MANAGER
				&& !actor.hasPermission(PermissionCodes.DEPARTMENT_MANAGE)) {
			throw ApiException.forbidden("CANNOT_ASSIGN_MANAGER", "Only administrators can remove a manager membership");
		}
		departmentMemberRepository.delete(member);
		auditService.record(AuditAction.DEPARTMENT_MEMBER_REMOVED, actor.id(), "DEPARTMENT", departmentId,
				Map.of("userId", userId), client);
		return get(departmentId, actor);
	}

	/** System use (dev seeder): sets the manager only if the department has none. */
	@Transactional
	public void assignManagerIfUnset(Long departmentId, Long userId) {
		Department department = load(departmentId);
		if (department.getManager() == null) {
			department.setManager(userRepository.getReferenceById(userId));
		}
	}

	private Department load(Long id) {
		return departmentRepository.findWithManagerById(id)
			.orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "Department not found"));
	}

	private void assertCanManageMembers(AuthenticatedUser actor, Long departmentId) {
		if (!accessScopeService.canManageDepartment(actor, departmentId)) {
			throw ApiException.forbidden("FORBIDDEN", "You can only manage members of departments you manage");
		}
	}

	private User resolveManager(Long managerId) {
		if (managerId == null) {
			return null;
		}
		User manager = userRepository.findById(managerId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "Manager not found"));
		if (!manager.isActive()) {
			throw ApiException.badRequest("USER_DISABLED", "A disabled user cannot manage a department");
		}
		return manager;
	}

	private static Map<Long, Long> toMap(List<DepartmentCount> counts) {
		return counts.stream().collect(Collectors.toMap(DepartmentCount::getDepartmentId, DepartmentCount::getTotal));
	}

	private static Long idOf(User user) {
		return user == null ? null : user.getId();
	}

	private static Long actorId(AuthenticatedUser actor) {
		return actor == null ? null : actor.id();
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
