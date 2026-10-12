package com.teamops.ticket.repository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.hibernate.query.common.TemporalUnit;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.util.StringUtils;

import com.teamops.ticket.dto.TicketView;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.service.TicketAccess;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/** Composable ticket filters, mirroring {@link TicketAccess} for visibility. */
public final class TicketSpecifications {

	/** Statuses in which the SLA clock runs. */
	private static final Set<TicketStatus> RUNNING = EnumSet.of(TicketStatus.NEW, TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

	private TicketSpecifications() {
	}

	public static Specification<Ticket> all() {
		return (root, query, cb) -> cb.conjunction();
	}

	/** Tickets the actor may see: everything (ALL), or requested by / assigned to them, or on their team. */
	public static Specification<Ticket> visibleTo(TicketAccess access) {
		if (access.scope().isAll()) {
			return all();
		}
		Long me = access.me();
		Set<Long> team = access.teamDepartmentIds();
		return (root, query, cb) -> {
			Predicate mine = cb.or(cb.equal(root.get("requester").get("id"), me),
					cb.equal(root.get("assignee").get("id"), me));
			return team.isEmpty() ? mine : cb.or(mine, root.get("department").get("id").in(team));
		};
	}

	public static Specification<Ticket> view(TicketView view, Long me) {
		return switch (view) {
			case ALL -> all();
			case REQUESTED_BY_ME -> (root, query, cb) -> cb.equal(root.get("requester").get("id"), me);
			case ASSIGNED_TO_ME -> (root, query, cb) -> cb.equal(root.get("assignee").get("id"), me);
			case UNASSIGNED -> (root, query, cb) -> cb.isNull(root.get("assignee"));
		};
	}

	/** Matches the code (e.g. "TKT-000042" or "42") or the subject. */
	public static Specification<Ticket> matches(String search) {
		if (!StringUtils.hasText(search)) {
			return all();
		}
		String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
		return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("subject")), pattern, '\\'),
				cb.like(cb.lower(root.get("code")), pattern, '\\'));
	}

	public static Specification<Ticket> statusIn(Set<TicketStatus> statuses) {
		return statuses.isEmpty() ? all() : (root, query, cb) -> root.get("status").in(statuses);
	}

	public static Specification<Ticket> priorityIn(Set<TicketPriority> priorities) {
		return priorities.isEmpty() ? all() : (root, query, cb) -> root.get("priority").in(priorities);
	}

	public static Specification<Ticket> category(Long categoryId) {
		return categoryId == null ? all() : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
	}

	public static Specification<Ticket> department(Long departmentId) {
		return departmentId == null ? all()
				: (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
	}

	public static Specification<Ticket> assignee(Long assigneeId) {
		return assigneeId == null ? all() : (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
	}

	/**
	 * Tickets in any of {@code statuses}, or overdue when {@code overdue} is set: the SLA clock is running and the
	 * resolution due time, moved on by the minutes spent paused, has passed. Nothing selected matches every ticket.
	 */
	public static Specification<Ticket> statusOrOverdue(Set<TicketStatus> statuses, boolean overdue, Instant now) {
		if (statuses.isEmpty() && !overdue) {
			return all();
		}
		return (root, query, cb) -> {
			HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
			Expression<Instant> due = hcb.addDuration(root.<Instant>get("resolutionDueAt"),
					hcb.durationScaled(root.<Integer>get("slaPausedMinutes"), hcb.duration(1, TemporalUnit.MINUTE)));
			Predicate overdueNow = cb.and(root.get("status").in(RUNNING), cb.lessThan(due, now));
			if (statuses.isEmpty()) {
				return overdueNow;
			}
			Predicate inStatus = root.get("status").in(statuses);
			return overdue ? cb.or(inStatus, overdueNow) : inStatus;
		};
	}

	/** SLA clock running and past the (pause-adjusted) resolution due time. */
	public static Specification<Ticket> overdue(Instant now) {
		return statusOrOverdue(Set.of(), true, now);
	}

	/** {@code field} (an Instant attribute) within [from, to); either end may be open. */
	public static Specification<Ticket> instantBetween(String field, Instant from, Instant to) {
		if (from == null && to == null) {
			return all();
		}
		return (root, query, cb) -> {
			Predicate after = from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get(field), from);
			Predicate before = to == null ? cb.conjunction() : cb.lessThan(root.get(field), to);
			return cb.and(after, before);
		};
	}

	/** Open tickets, plus any ticket created since {@code since} (the SLA reporting window). */
	public static Specification<Ticket> openOrCreatedSince(Instant since) {
		return (root, query, cb) -> cb.or(root.get("status").in(TicketStatus.OPEN_STATUSES),
				cb.greaterThanOrEqualTo(root.get("slaStartAt"), since));
	}

	static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
