package com.teamops.module.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.audit.RecordTypes;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.module.dto.ModuleDtos.ModuleSettings;
import com.teamops.module.entity.DepartmentModule;
import com.teamops.module.repository.DepartmentModuleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Admin settings for department tools (MODULE_MANAGE, Super Admin only by default): every registered module with the
 * departments it is switched on for, and the switch per department. Each change is audited (MODULE_ENABLED,
 * MODULE_DISABLED); setting a switch to the state it already has changes nothing and records nothing.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ModuleAdminService {

	private static final Comparator<DepartmentSummary> BY_NAME = Comparator.comparing(DepartmentSummary::name,
			String.CASE_INSENSITIVE_ORDER);

	private final ModuleRegistry registry;

	private final DepartmentModuleRepository departmentModules;

	private final DepartmentRepository departments;

	private final AuditService auditService;

	@Transactional(readOnly = true)
	public List<ModuleSettings> list() {
		Map<String, List<DepartmentSummary>> enabled = departmentModules.findByEnabledTrue()
			.stream()
			.collect(Collectors.groupingBy(DepartmentModule::getModuleKey, LinkedHashMap::new,
					Collectors.mapping(row -> DepartmentSummary.of(row.getDepartment()), Collectors.toList())));
		return registry.all()
			.stream()
			.map(module -> ModuleSettings.of(module,
					enabled.getOrDefault(module.key(), List.of()).stream().sorted(BY_NAME).toList()))
			.toList();
	}

	public ModuleSettings setEnabled(String key, Long departmentId, boolean enabled, AuthenticatedUser actor,
			ClientInfo client) {
		ModuleDefinition module = registry.require(key);
		Department department = departments.findById(departmentId)
			.orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "Department not found"));
		DepartmentModule row = departmentModules.findByModuleKeyAndDepartmentId(key, departmentId).orElse(null);
		boolean current = row != null && row.isEnabled();
		if (current != enabled) {
			if (enabled && department.getStatus() != DepartmentStatus.ACTIVE) {
				throw ApiException.badRequest("DEPARTMENT_INACTIVE",
						"Switch tools on for active departments only: " + department.getName() + " is inactive");
			}
			if (row == null) {
				row = new DepartmentModule(key, department);
			}
			row.setEnabled(enabled);
			departmentModules.saveAndFlush(row);
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("module", module.key());
			details.put("moduleName", module.name());
			details.put("departmentId", department.getId());
			details.put("department", department.getName());
			auditService.record(enabled ? AuditAction.MODULE_ENABLED : AuditAction.MODULE_DISABLED, actor.id(),
					RecordTypes.typeOf(DepartmentModule.class), row.getId(), details, client);
		}
		return list().stream().filter(settings -> settings.key().equals(key)).findFirst().orElseThrow();
	}

}
