package com.teamops.workload;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.repository.UserSpecifications;
import com.teamops.workload.WorkloadDtos.Criteria;
import com.teamops.workload.WorkloadDtos.Response;
import com.teamops.workload.WorkloadDtos.Row;
import com.teamops.workload.WorkloadDtos.Summary;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * Workload per person (brief section 9). Who appears depends on scope: everyone for a Super Admin, people in managed
 * departments for a manager, and only yourself otherwise. Active people with no tasks are listed at 0%.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkloadService {

	static final int COMPLETED_LOOKBACK_DAYS = 30;

	private static final int MAX_PEOPLE = 500;

	private final UserRepository userRepository;

	private final WorkloadQuery workloadQuery;

	private final AccessScopeService accessScopeService;

	private final AppSettingsService settings;

	private final BusinessCalendar calendar;

	public Response workload(Criteria criteria, AuthenticatedUser actor) {
		if (!actor.hasPermission("WORKLOAD_VIEW") && !actor.id().equals(criteria.userId())) {
			throw ApiException.forbidden("FORBIDDEN", "You can only see your own workload");
		}
		if ((criteria.from() == null) != (criteria.to() == null)
				|| (criteria.from() != null && criteria.to().isBefore(criteria.from()))) {
			throw ApiException.badRequest("INVALID_DATES", "Provide both 'from' and 'to', with 'to' on or after 'from'");
		}

		LocalDate today = calendar.today();
		int windowDays = settings.workloadWindowDays();
		BigDecimal defaultHours = settings.defaultTaskHours();
		List<User> people = peopleInScope(criteria, accessScopeService.scopeFor(actor), actor);

		Map<Long, WorkloadQuery.Counts> counts = workloadQuery.counts(people.stream().map(User::getId).toList(),
				today, today.plusDays(windowDays), defaultHours, criteria.statuses(), criteria.priorities(),
				criteria.from(), criteria.to(), criteria.from() == null ? null : calendar.startOf(criteria.from()),
				criteria.to() == null ? null : calendar.startOf(criteria.to().plusDays(1)),
				calendar.startOf(today.minusDays(COMPLETED_LOOKBACK_DAYS)));

		List<Row> rows = people.stream()
			.map(person -> toRow(person, counts.getOrDefault(person.getId(), WorkloadQuery.Counts.EMPTY), windowDays))
			.sorted(comparator(criteria.sort()))
			.toList();
		return new Response(today, windowDays, defaultHours, criteria.from(), criteria.to(), summarise(rows), rows);
	}

	private List<User> peopleInScope(Criteria criteria, AccessScope scope, AuthenticatedUser actor) {
		var spec = UserSpecifications.withStatus(UserStatus.ACTIVE)
			.and(UserSpecifications.inDepartment(criteria.departmentId()))
			.and(UserSpecifications.matches(criteria.search()))
			.and((root, query, cb) -> criteria.userId() == null ? cb.conjunction()
					: cb.equal(root.get("id"), criteria.userId()))
			.and((root, query, cb) -> {
				if (scope.isAll()) {
					return cb.conjunction();
				}
				Predicate self = cb.equal(root.get("id"), actor.id());
				return scope.kind() == AccessScope.Kind.DEPARTMENTS
						? cb.or(self, root.get("department").get("id").in(scope.departmentIds())) : self;
			});
		return userRepository
			.findAll(spec, PageRequest.of(0, MAX_PEOPLE, Sort.by("firstName", "lastName")))
			.getContent();
	}

	private static Row toRow(User person, WorkloadQuery.Counts c, int windowDays) {
		int percent = WorkloadCalculator.percent(c.remainingHours(), person.getWeeklyCapacityHours(), windowDays);
		return new Row(UserSummary.of(person), DepartmentSummary.of(person.getDepartment()), c.total(), c.todo(),
				c.inProgress(), c.blocked(), c.inReview(), c.completed(), c.overdue(), c.dueToday(), c.active(),
				c.remainingHours().setScale(1, RoundingMode.HALF_UP),
				WorkloadCalculator.capacityHours(person.getWeeklyCapacityHours(), windowDays), percent,
				WorkloadLevel.of(percent));
	}

	static Comparator<Row> comparator(WorkloadDtos.Sort sort) {
		Comparator<Row> byName = Comparator.comparing(row -> row.user().fullName());
		Comparator<Row> primary = switch (sort) {
			case HIGHEST -> Comparator.comparingInt(Row::workloadPercent).reversed();
			case LOWEST -> Comparator.comparingInt(Row::workloadPercent);
			case MOST_OVERDUE -> Comparator.comparingLong(Row::overdue).reversed();
			case MOST_COMPLETED -> Comparator.comparingLong(Row::completed).reversed();
			case MOST_ACTIVE -> Comparator.comparingLong(Row::activeTasks).reversed();
			case NAME -> byName;
		};
		return primary.thenComparing(byName);
	}

	static Summary summarise(List<Row> rows) {
		Map<WorkloadLevel, Long> byLevel = new EnumMap<>(WorkloadLevel.class);
		for (WorkloadLevel level : WorkloadLevel.values()) {
			byLevel.put(level, 0L);
		}
		long active = 0;
		long overdue = 0;
		long dueToday = 0;
		long percentTotal = 0;
		for (Row row : rows) {
			byLevel.merge(row.level(), 1L, Long::sum);
			active += row.activeTasks();
			overdue += row.overdue();
			dueToday += row.dueToday();
			percentTotal += row.workloadPercent();
		}
		int average = rows.isEmpty() ? 0 : Math.round((float) percentTotal / rows.size());
		return new Summary(rows.size(), byLevel, active, overdue, dueToday, average);
	}

}
