package com.teamops.approval.repository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.teamops.approval.entity.Approval;
import com.teamops.approval.entity.ApprovalStatus;
import com.teamops.approval.entity.ApprovalStep;
import com.teamops.approval.entity.StepStatus;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.user.entity.Role;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Approval filters. A request is visible to its requester, to anyone named on (or holding the role of) one of its
 * steps, to managers of its department, and to Super Admins. Step checks use subqueries so paging stays correct.
 */
public final class ApprovalSpecifications {

	private ApprovalSpecifications() {
	}

	public static Specification<Approval> all() {
		return (root, query, cb) -> cb.conjunction();
	}

	public static Specification<Approval> visibleTo(AuthenticatedUser actor, AccessScope scope) {
		if (scope.isAll()) {
			return all();
		}
		return (root, query, cb) -> {
			Predicate mine = cb.equal(root.get("requester").get("id"), actor.id());
			Predicate approver = root.get("id").in(stepsFor(query, cb, actor, null));
			Predicate department = scope.departmentIds().isEmpty() ? cb.disjunction()
					: root.get("department").get("id").in(scope.departmentIds());
			return cb.or(mine, approver, department);
		};
	}

	/** Pending requests whose current step the actor may decide (never their own requests). */
	public static Specification<Approval> awaitingDecisionBy(AuthenticatedUser actor) {
		return (root, query, cb) -> cb.and(cb.equal(root.get("status"), ApprovalStatus.PENDING),
				cb.notEqual(root.get("requester").get("id"), actor.id()),
				root.get("id").in(stepsFor(query, cb, actor, StepStatus.PENDING)));
	}

	public static Specification<Approval> requestedBy(Long userId) {
		return (root, query, cb) -> cb.equal(root.get("requester").get("id"), userId);
	}

	public static Specification<Approval> statusIn(Set<ApprovalStatus> statuses) {
		return statuses.isEmpty() ? all() : (root, query, cb) -> root.get("status").in(statuses);
	}

	public static Specification<Approval> type(Long typeId) {
		return typeId == null ? all() : (root, query, cb) -> cb.equal(root.get("type").get("id"), typeId);
	}

	public static Specification<Approval> dueBetween(LocalDate from, LocalDate to) {
		return (root, query, cb) -> cb.between(root.get("dueDate"), from, to);
	}

	public static Specification<Approval> matches(String search) {
		if (!StringUtils.hasText(search)) {
			return all();
		}
		String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
			.replace("_", "\\_") + "%";
		return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
				cb.like(cb.lower(root.get("code")), pattern, '\\'));
	}

	/** Ids of requests with a step naming the actor or one of their roles (optionally in a given status). */
	private static Subquery<Long> stepsFor(CriteriaQuery<?> query, CriteriaBuilder cb, AuthenticatedUser actor,
			StepStatus status) {
		Subquery<Long> subquery = query.subquery(Long.class);
		Root<ApprovalStep> step = subquery.from(ApprovalStep.class);
		Join<ApprovalStep, Role> role = step.join("approverRole", JoinType.LEFT);
		Predicate who = actor.roles().isEmpty() ? cb.equal(step.get("approver").get("id"), actor.id())
				: cb.or(cb.equal(step.get("approver").get("id"), actor.id()), role.get("code").in(actor.roles()));
		Predicate where = status == null ? who : cb.and(who, cb.equal(step.get("status"), status));
		return subquery.select(step.get("approval").get("id")).where(where);
	}

}
