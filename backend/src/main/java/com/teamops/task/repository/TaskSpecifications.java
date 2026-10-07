package com.teamops.task.repository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.teamops.common.security.AccessScope;
import com.teamops.task.dto.DueFilter;
import com.teamops.task.dto.TaskView;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.user.entity.User;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Composable task filters. Relationship checks use subqueries so paging is never affected by duplicate rows. */
public final class TaskSpecifications {

	private TaskSpecifications() {
	}

	public static Specification<Task> all() {
		return (root, query, cb) -> cb.conjunction();
	}

	/**
	 * Tasks the viewer may see: everything (ALL), tasks in managed departments (DEPARTMENTS), and always the tasks
	 * they are assigned to, created, or watch.
	 */
	public static Specification<Task> visibleTo(AccessScope scope) {
		if (scope.isAll()) {
			return all();
		}
		Long me = scope.userId();
		return (root, query, cb) -> {
			Predicate involved = cb.or(cb.equal(root.get("assignee").get("id"), me),
					cb.equal(root.get("createdBy").get("id"), me), root.get("id").in(watchedBy(query, cb, me)));
			if (scope.kind() == AccessScope.Kind.DEPARTMENTS && !scope.departmentIds().isEmpty()) {
				return cb.or(root.get("department").get("id").in(scope.departmentIds()), involved);
			}
			return involved;
		};
	}

	public static Specification<Task> view(TaskView view, Long me) {
		return switch (view) {
			case ALL -> all();
			case ASSIGNED_TO_ME -> (root, query, cb) -> cb.equal(root.get("assignee").get("id"), me);
			case CREATED_BY_ME -> (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), me);
			case WATCHING -> (root, query, cb) -> root.get("id").in(watchedBy(query, cb, me));
		};
	}

	/** Matches the code (e.g. "TSK-000042" or "42") or the title. */
	public static Specification<Task> matches(String search) {
		if (!StringUtils.hasText(search)) {
			return all();
		}
		String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
		return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
				cb.like(cb.lower(root.get("code")), pattern, '\\'));
	}

	public static Specification<Task> statusIn(Set<TaskStatus> statuses) {
		return statuses.isEmpty() ? all() : (root, query, cb) -> root.get("status").in(statuses);
	}

	public static Specification<Task> priorityIn(Set<TaskPriority> priorities) {
		return priorities.isEmpty() ? all() : (root, query, cb) -> root.get("priority").in(priorities);
	}

	public static Specification<Task> assignee(Long assigneeId) {
		return assigneeId == null ? all() : (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
	}

	public static Specification<Task> department(Long departmentId) {
		return departmentId == null ? all()
				: (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
	}

	public static Specification<Task> project(Long projectId) {
		return projectId == null ? all() : (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
	}

	/** Due-date views only ever match active (open) tasks. */
	public static Specification<Task> due(DueFilter due, LocalDate today) {
		if (due == null) {
			return all();
		}
		return (root, query, cb) -> {
			Predicate active = root.get("status").in(TaskStatus.ACTIVE);
			Predicate condition = switch (due) {
				case OVERDUE -> cb.lessThan(root.get("dueDate"), today);
				case TODAY -> cb.equal(root.get("dueDate"), today);
				case UPCOMING -> cb.and(cb.greaterThan(root.get("dueDate"), today),
						cb.lessThanOrEqualTo(root.get("dueDate"), today.plusDays(7)));
				case NO_DUE_DATE -> cb.isNull(root.get("dueDate"));
			};
			return cb.and(active, condition);
		};
	}

	private static Subquery<Long> watchedBy(jakarta.persistence.criteria.CriteriaQuery<?> query,
			jakarta.persistence.criteria.CriteriaBuilder cb, Long userId) {
		Subquery<Long> subquery = query.subquery(Long.class);
		Root<Task> task = subquery.from(Task.class);
		Join<Task, User> watcher = task.join("watchers");
		return subquery.select(task.get("id")).where(cb.equal(watcher.get("id"), userId));
	}

	static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
