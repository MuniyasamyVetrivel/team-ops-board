package com.teamops.project.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.project.entity.MilestoneStatus;
import com.teamops.project.entity.Project;
import com.teamops.project.entity.ProjectMilestone;
import com.teamops.project.entity.ProjectRisk;
import com.teamops.project.entity.ProjectStatus;
import com.teamops.project.entity.RiskLevel;
import com.teamops.project.entity.RiskStatus;
import com.teamops.project.repository.ProjectQuery.MilestoneCounts;
import com.teamops.project.repository.ProjectQuery.TaskCounts;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Project API records. Progress and risk severity are computed per request. */
public final class ProjectDtos {

	private ProjectDtos() {
	}

	/**
	 * Progress %: the manual override when set, otherwise completed ÷ (all tasks except cancelled) × 100, rounded.
	 * {@code null} when there is no override and no task ("—").
	 */
	public static Integer progress(Integer override, TaskCounts tasks) {
		if (override != null) {
			return override;
		}
		return tasks.total() == 0 ? null : (int) Math.round(tasks.completed() * 100.0 / tasks.total());
	}

	public record ProjectListItem(Long id, String code, String name, ProjectStatus status,
			DepartmentSummary department, UserSummary owner, LocalDate startDate, LocalDate endDate, Integer progress,
			boolean progressOverridden, TaskCounts tasks, MilestoneCounts milestones, long openRisks,
			Instant updatedAt) {

		public static ProjectListItem of(Project project, TaskCounts tasks, MilestoneCounts milestones,
				long openRisks) {
			return new ProjectListItem(project.getId(), project.getCode(), project.getName(), project.getStatus(),
					DepartmentSummary.of(project.getDepartment()), UserSummary.of(project.getOwner()),
					project.getStartDate(), project.getEndDate(), ProjectDtos.progress(project.getProgressOverride(), tasks),
					project.getProgressOverride() != null, tasks, milestones, openRisks, project.getUpdatedAt());
		}

	}

	/** Milestone with {@code overdue} computed against the business-zone "today". */
	public record MilestoneResponse(Long id, String name, String description, LocalDate dueDate,
			MilestoneStatus status, Instant completedAt, int position, boolean overdue) {

		public static MilestoneResponse of(ProjectMilestone milestone, LocalDate today) {
			boolean overdue = milestone.getStatus() != MilestoneStatus.COMPLETED && milestone.getDueDate() != null
					&& milestone.getDueDate().isBefore(today);
			return new MilestoneResponse(milestone.getId(), milestone.getName(), milestone.getDescription(),
					milestone.getDueDate(), milestone.getStatus(), milestone.getCompletedAt(), milestone.getPosition(),
					overdue);
		}

	}

	/** Severity = probability rank × impact rank: 1–2 low, 3–4 medium, 6–9 high. */
	public record RiskResponse(Long id, String title, String description, RiskLevel probability, RiskLevel impact,
			int severity, String mitigation, UserSummary owner, RiskStatus status) {

		public static RiskResponse of(ProjectRisk risk) {
			return new RiskResponse(risk.getId(), risk.getTitle(), risk.getDescription(), risk.getProbability(),
					risk.getImpact(), risk.getProbability().rank() * risk.getImpact().rank(), risk.getMitigation(),
					UserSummary.of(risk.getOwner()), risk.getStatus());
		}

	}

	public record ProjectPermissions(boolean canEdit) {

	}

	public record ProjectDetail(Long id, String code, String name, String description, ProjectStatus status,
			DepartmentSummary department, UserSummary owner, LocalDate startDate, LocalDate endDate, Integer progress,
			Integer progressOverride, TaskCounts tasks, List<UserSummary> members,
			List<MilestoneResponse> milestones, List<RiskResponse> risks, List<ProjectRef> dependencies,
			Integer version, Instant createdAt, Instant updatedAt, ProjectPermissions permissions) {

	}

	public record CreateProject(
			@NotBlank(message = "Name is required") @Size(max = 200) String name,
			@Size(max = 20000) String description,
			@NotNull(message = "Department is required") Long departmentId,
			Long ownerId,
			LocalDate startDate,
			LocalDate endDate) {

	}

	/** Full replacement of the editable fields. {@code progressOverride = null} returns to task-based progress. */
	public record UpdateProject(
			@NotNull(message = "Version is required") Integer version,
			@NotBlank(message = "Name is required") @Size(max = 200) String name,
			@Size(max = 20000) String description,
			@NotNull(message = "Department is required") Long departmentId,
			Long ownerId,
			LocalDate startDate,
			LocalDate endDate,
			@NotNull(message = "Status is required") ProjectStatus status,
			@Min(value = 0, message = "0–100") @Max(value = 100, message = "0–100") Integer progressOverride) {

	}

	public record SaveMilestone(
			@NotBlank(message = "Name is required") @Size(max = 200) String name,
			@Size(max = 5000) String description,
			LocalDate dueDate,
			MilestoneStatus status) {

	}

	public record SaveRisk(
			@NotBlank(message = "Title is required") @Size(max = 200) String title,
			@Size(max = 5000) String description,
			@NotNull(message = "Probability is required") RiskLevel probability,
			@NotNull(message = "Impact is required") RiskLevel impact,
			@Size(max = 5000) String mitigation,
			Long ownerId,
			RiskStatus status) {

	}

	public record Member(@NotNull(message = "User is required") Long userId) {

	}

	public record Dependency(@NotNull(message = "Project is required") Long projectId) {

	}

}
