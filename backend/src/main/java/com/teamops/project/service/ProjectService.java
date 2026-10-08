package com.teamops.project.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.project.dto.ProjectDtos;
import com.teamops.project.dto.ProjectDtos.CreateProject;
import com.teamops.project.dto.ProjectDtos.MilestoneResponse;
import com.teamops.project.dto.ProjectDtos.ProjectDetail;
import com.teamops.project.dto.ProjectDtos.ProjectListItem;
import com.teamops.project.dto.ProjectDtos.ProjectPermissions;
import com.teamops.project.dto.ProjectDtos.RiskResponse;
import com.teamops.project.dto.ProjectDtos.SaveMilestone;
import com.teamops.project.dto.ProjectDtos.SaveRisk;
import com.teamops.project.dto.ProjectDtos.UpdateProject;
import com.teamops.project.dto.ProjectRef;
import com.teamops.project.entity.MilestoneStatus;
import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectMilestone;
import com.teamops.project.entity.ProjectRisk;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.project.entity.RiskStatus;
import com.teamops.project.repository.ProjectMilestoneRepository;
import com.teamops.project.repository.ProjectQuery;
import com.teamops.project.repository.ProjectRepository;
import com.teamops.project.repository.ProjectRiskRepository;
import com.teamops.project.repository.ProjectSpecifications;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Projects (brief section 13): header, members, milestones, risks and dependencies. Progress comes from the
 * project's tasks unless overridden. Every action is checked with {@link ProjectAccess}; projects the actor cannot
 * see are reported as not found.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

	private static final EnumSet<ProjectStatus> OPEN = EnumSet.of(ProjectStatus.PLANNING, ProjectStatus.ACTIVE,
			ProjectStatus.ON_HOLD);

	private final ProjectRepository projectRepository;

	private final ProjectMilestoneRepository milestoneRepository;

	private final ProjectRiskRepository riskRepository;

	private final ProjectQuery projectQuery;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final CodeGenerator codeGenerator;

	private final AccessScopeService accessScopeService;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	/** Projects that can still take new tasks. */
	public List<ProjectRef> openProjects() {
		return projectRepository.findByStatusInOrderByNameAsc(OPEN).stream().map(ProjectRef::of).toList();
	}

	public PageResponse<ProjectListItem> search(String search, Set<ProjectStatus> statuses, Long departmentId,
			Pageable pageable, AuthenticatedUser actor) {
		var spec = ProjectSpecifications.visibleTo(access(actor))
			.and(ProjectSpecifications.matches(search))
			.and(ProjectSpecifications.statusIn(statuses == null ? Set.of() : statuses))
			.and(ProjectSpecifications.department(departmentId));
		var page = projectRepository.findAll(spec, pageable);
		ProjectQuery.Stats stats = projectQuery.stats(page.getContent().stream().map(Project::getId).toList(),
				calendar.today());
		return PageResponse.of(page.map(project -> ProjectListItem.of(project, stats.tasksOf(project.getId()),
				stats.milestonesOf(project.getId()), stats.openRisksOf(project.getId()))));
	}

	public ProjectDetail get(Long id, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		return toDetail(loadVisible(id, access), access);
	}

	// --- project --------------------------------------------------------------------------------------------

	@Transactional
	public ProjectDetail create(CreateProject request, AuthenticatedUser actor, ClientInfo client) {
		ProjectAccess access = access(actor);
		if (!access.canCreateIn(request.departmentId())) {
			throw ApiException.forbidden("FORBIDDEN", "You can only create projects in departments you manage");
		}
		validateDates(request.startDate(), request.endDate());
		Project project = new Project();
		project.setCode(codeGenerator.next(CodeGenerator.PROJECT));
		project.setName(request.name().trim());
		project.setDescription(trimToNull(request.description()));
		project.setDepartment(resolveActiveDepartment(request.departmentId()));
		project.setOwner(request.ownerId() == null ? userRepository.getReferenceById(actor.id())
				: resolveUser(request.ownerId()));
		project.setStartDate(request.startDate());
		project.setEndDate(request.endDate());
		Project saved = projectRepository.save(project);
		auditService.record(AuditAction.PROJECT_CREATED, actor.id(), "PROJECT", saved.getId(),
				Map.of("code", saved.getCode(), "departmentId", request.departmentId()), client);
		return toDetail(saved, access);
	}

	@Transactional
	public ProjectDetail update(Long id, UpdateProject request, AuthenticatedUser actor, ClientInfo client) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		if (!Objects.equals(project.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this project just now. Reload and try again.");
		}
		validateDates(request.startDate(), request.endDate());
		if (!project.getDepartment().getId().equals(request.departmentId())) {
			if (!access.canCreateIn(request.departmentId())) {
				throw ApiException.forbidden("FORBIDDEN", "You cannot move projects into that department");
			}
			project.setDepartment(resolveActiveDepartment(request.departmentId()));
		}
		User owner = request.ownerId() == null ? null : resolveUser(request.ownerId());
		AuditChanges changes = new AuditChanges().track("name", project.getName(), request.name().trim())
			.track("status", project.getStatus(), request.status())
			.track("ownerId", userId(project.getOwner()), userId(owner))
			.track("startDate", project.getStartDate(), request.startDate())
			.track("endDate", project.getEndDate(), request.endDate())
			.track("progressOverride", project.getProgressOverride(), request.progressOverride());
		project.setName(request.name().trim());
		project.setDescription(trimToNull(request.description()));
		project.setOwner(owner);
		project.setStartDate(request.startDate());
		project.setEndDate(request.endDate());
		project.setStatus(request.status());
		project.setProgressOverride(request.progressOverride());
		projectRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.PROJECT_UPDATED, actor.id(), "PROJECT", project.getId(),
					changes.toDetails(), client);
		}
		return toDetail(project, access);
	}

	// --- members --------------------------------------------------------------------------------------------

	@Transactional
	public ProjectDetail addMember(Long id, Long userId, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		if (!project.hasMember(userId)) {
			project.getMembers().add(resolveUser(userId));
		}
		projectRepository.flush();
		return toDetail(project, access);
	}

	@Transactional
	public ProjectDetail removeMember(Long id, Long userId, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		project.getMembers().removeIf(user -> user.getId().equals(userId));
		projectRepository.flush();
		return toDetail(project, access);
	}

	// --- milestones -----------------------------------------------------------------------------------------

	@Transactional
	public ProjectDetail addMilestone(Long id, SaveMilestone request, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		ProjectMilestone milestone = new ProjectMilestone();
		milestone.setProject(project);
		milestone.setPosition(milestoneRepository.maxPosition(id) + 1);
		applyMilestone(milestone, request);
		milestoneRepository.save(milestone);
		return toDetail(project, access);
	}

	@Transactional
	public ProjectDetail updateMilestone(Long id, Long milestoneId, SaveMilestone request, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		applyMilestone(loadMilestone(project, milestoneId), request);
		milestoneRepository.flush();
		return toDetail(project, access);
	}

	@Transactional
	public ProjectDetail deleteMilestone(Long id, Long milestoneId, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		milestoneRepository.delete(loadMilestone(project, milestoneId));
		return toDetail(project, access);
	}

	// --- risks ----------------------------------------------------------------------------------------------

	@Transactional
	public ProjectDetail addRisk(Long id, SaveRisk request, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		ProjectRisk risk = new ProjectRisk();
		risk.setProject(project);
		applyRisk(risk, request);
		riskRepository.save(risk);
		return toDetail(project, access);
	}

	@Transactional
	public ProjectDetail updateRisk(Long id, Long riskId, SaveRisk request, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		applyRisk(loadRisk(project, riskId), request);
		riskRepository.flush();
		return toDetail(project, access);
	}

	@Transactional
	public ProjectDetail deleteRisk(Long id, Long riskId, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		riskRepository.delete(loadRisk(project, riskId));
		return toDetail(project, access);
	}

	// --- dependencies ---------------------------------------------------------------------------------------

	/** "This project depends on X": rejects self-references and cycles. */
	@Transactional
	public ProjectDetail addDependency(Long id, Long dependsOnId, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		if (id.equals(dependsOnId)) {
			throw ApiException.badRequest("INVALID_DEPENDENCY", "A project cannot depend on itself");
		}
		Project target = loadVisible(dependsOnId, access);
		if (projectRepository.countDependencyPath(dependsOnId, id) > 0) {
			throw ApiException.badRequest("DEPENDENCY_CYCLE",
					target.getCode() + " already depends on " + project.getCode() + "; this would create a loop");
		}
		project.getDependsOn().add(target);
		projectRepository.flush();
		return toDetail(project, access);
	}

	@Transactional
	public ProjectDetail removeDependency(Long id, Long dependsOnId, AuthenticatedUser actor) {
		ProjectAccess access = access(actor);
		Project project = editable(id, access);
		project.getDependsOn().removeIf(p -> p.getId().equals(dependsOnId));
		projectRepository.flush();
		return toDetail(project, access);
	}

	// --- shared helpers -------------------------------------------------------------------------------------

	public ProjectAccess access(AuthenticatedUser actor) {
		return new ProjectAccess(actor, accessScopeService.scopeFor(actor));
	}

	Project loadVisible(Long id, ProjectAccess access) {
		return projectRepository.findDetailedById(id)
			.filter(access::canView)
			.orElseThrow(() -> ApiException.notFound("PROJECT_NOT_FOUND", "Project not found"));
	}

	private Project editable(Long id, ProjectAccess access) {
		Project project = loadVisible(id, access);
		if (!access.canEdit(project)) {
			throw ApiException.forbidden("FORBIDDEN", "You do not have permission to change this project");
		}
		return project;
	}

	private ProjectDetail toDetail(Project project, ProjectAccess access) {
		LocalDate today = calendar.today();
		ProjectQuery.Stats stats = projectQuery.stats(List.of(project.getId()), today);
		ProjectQuery.TaskCounts tasks = stats.tasksOf(project.getId());
		return new ProjectDetail(project.getId(), project.getCode(), project.getName(), project.getDescription(),
				project.getStatus(), DepartmentSummary.of(project.getDepartment()), UserSummary.of(project.getOwner()),
				project.getStartDate(), project.getEndDate(),
				ProjectDtos.progress(project.getProgressOverride(), tasks), project.getProgressOverride(), tasks,
				project.getMembers()
					.stream()
					.sorted(Comparator.comparing(User::getFullName))
					.map(UserSummary::of)
					.toList(),
				milestoneRepository.findByProjectIdOrderByPositionAscIdAsc(project.getId())
					.stream()
					.map(m -> MilestoneResponse.of(m, today))
					.toList(),
				riskRepository.findByProjectIdOrderByIdAsc(project.getId()).stream().map(RiskResponse::of).toList(),
				project.getDependsOn()
					.stream()
					.sorted(Comparator.comparing(Project::getCode))
					.map(ProjectRef::of)
					.toList(),
				project.getVersion(), project.getCreatedAt(), project.getUpdatedAt(),
				new ProjectPermissions(access.canEdit(project)));
	}

	private void applyMilestone(ProjectMilestone milestone, SaveMilestone request) {
		milestone.setName(request.name().trim());
		milestone.setDescription(trimToNull(request.description()));
		milestone.setDueDate(request.dueDate());
		MilestoneStatus status = request.status() == null ? MilestoneStatus.PLANNED : request.status();
		if (status == MilestoneStatus.COMPLETED && milestone.getStatus() != MilestoneStatus.COMPLETED) {
			milestone.setCompletedAt(calendar.now());
		}
		else if (status != MilestoneStatus.COMPLETED) {
			milestone.setCompletedAt(null);
		}
		milestone.setStatus(status);
	}

	private void applyRisk(ProjectRisk risk, SaveRisk request) {
		risk.setTitle(request.title().trim());
		risk.setDescription(trimToNull(request.description()));
		risk.setProbability(request.probability());
		risk.setImpact(request.impact());
		risk.setMitigation(trimToNull(request.mitigation()));
		risk.setOwner(request.ownerId() == null ? null : resolveUser(request.ownerId()));
		risk.setStatus(request.status() == null ? RiskStatus.OPEN : request.status());
	}

	private ProjectMilestone loadMilestone(Project project, Long milestoneId) {
		return milestoneRepository.findById(milestoneId)
			.filter(m -> m.getProject().getId().equals(project.getId()))
			.orElseThrow(() -> ApiException.notFound("MILESTONE_NOT_FOUND", "Milestone not found"));
	}

	private ProjectRisk loadRisk(Project project, Long riskId) {
		return riskRepository.findById(riskId)
			.filter(r -> r.getProject().getId().equals(project.getId()))
			.orElseThrow(() -> ApiException.notFound("RISK_NOT_FOUND", "Risk not found"));
	}

	private Department resolveActiveDepartment(Long departmentId) {
		Department department = departmentRepository.findById(departmentId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		if (department.getStatus() != DepartmentStatus.ACTIVE) {
			throw ApiException.badRequest("DEPARTMENT_INACTIVE", "Projects cannot be added to an inactive department");
		}
		return department;
	}

	private User resolveUser(Long userId) {
		return userRepository.findById(userId)
			.filter(User::isActive)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "User not found or disabled"));
	}

	private static void validateDates(LocalDate start, LocalDate end) {
		if (start != null && end != null && end.isBefore(start)) {
			throw ApiException.badRequest("INVALID_DATES", "The end date cannot be before the start date");
		}
	}

	private static Long userId(User user) {
		return user == null ? null : user.getId();
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
