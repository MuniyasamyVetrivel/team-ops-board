package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.TargetProgress;
import com.teamops.marketing.common.TargetProgress.TargetStatus;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.marketing.target.dto.TargetDtos.CreateTarget;
import com.teamops.marketing.target.dto.TargetDtos.MonthlyTargetEntry;
import com.teamops.marketing.target.dto.TargetDtos.MonthlyTargets;
import com.teamops.marketing.target.dto.TargetDtos.SetMonthlyResult;
import com.teamops.marketing.target.dto.TargetDtos.SetMonthlyTargets;
import com.teamops.marketing.target.dto.TargetDtos.StatusSummary;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.marketing.target.dto.TargetDtos.TargetTrend;
import com.teamops.marketing.target.dto.TargetDtos.TrendPoint;
import com.teamops.marketing.target.dto.TargetDtos.TrendView;
import com.teamops.marketing.target.dto.TargetDtos.UpdateTarget;
import com.teamops.marketing.target.entity.MarketingTarget;
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.marketing.target.repository.MarketingTargetRepository;
import com.teamops.marketing.target.repository.TargetTypeRepository;
import com.teamops.marketing.target.service.TargetRules.ResolvedActual;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Monthly marketing targets (brief sections 30–34). Each type has at most one target per month; achievement %,
 * remaining and status are computed with {@link TargetProgress} and the type's (or the global) behind threshold.
 * Actuals of automatic types are aggregated by {@link TargetActuals}; a stored hand-entered actual always wins. Future
 * months are "planned": they have no actual and no status yet.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TargetService {

	public static final String TARGET_EDIT = "TARGET_EDIT";

	/** Years shown by the YEAR trend view, ending with the requested year. */
	static final int TREND_YEARS = 5;

	private static final String ENTITY = "MARKETING_TARGET";

	private final MarketingTargetRepository targetRepository;

	private final TargetTypeRepository typeRepository;

	private final TargetTypeService typeService;

	private final TargetActuals actuals;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final AppSettingsService settings;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	// --- reading ----------------------------------------------------------------------------------------------

	/** The target performance table for a month; {@code status} filters on the computed status. */
	public MonthlyTargets monthly(MarketingPeriod period, Long ownerId, Set<TargetStatus> statuses,
			AuthenticatedUser viewer) {
		List<MarketingTarget> all = targetRepository.findByPeriod(period.month(), period.year());
		List<TargetItem> items = toItems(all, period, viewer);
		List<TargetItem> shown = items.stream()
			.filter(item -> ownerId == null || (item.owner() != null && item.owner().id().equals(ownerId)))
			.filter(item -> statuses == null || statuses.isEmpty() || statuses.contains(item.status()))
			.toList();
		Set<Long> withTarget = all.stream().map(t -> t.getType().getId()).collect(Collectors.toSet());
		return new MonthlyTargets(Period.of(period), shown, summarise(shown),
				typeRepository.findAllByOrderByPositionAscNameAsc()
					.stream()
					.filter(type -> type.isActive() && !withTarget.contains(type.getId()))
					.map(typeService::ref)
					.toList());
	}

	public TargetItem get(Long id, AuthenticatedUser viewer) {
		MarketingTarget target = load(id);
		return toItems(List.of(target), periodOf(target), viewer).getFirst();
	}

	/**
	 * A type's targets against actuals by month (the 12 months of {@code year}), quarter (its four quarters) or year
	 * (five years ending with {@code year}).
	 */
	public TargetTrend trend(Long typeId, TrendView view, int year) {
		TargetType type = typeService.load(typeId);
		LocalDate today = calendar.today();
		BigDecimal threshold = typeService.thresholdOf(type, settings.marketingBehindThresholdPct());
		List<List<MarketingPeriod>> buckets = buckets(view, year);
		MarketingPeriod from = buckets.getFirst().getFirst();
		MarketingPeriod to = buckets.getLast().getLast();
		Map<MarketingPeriod, MarketingTarget> targets = targetRepository
			.findForTrend(typeId, key(from), key(to))
			.stream()
			.collect(Collectors.toMap(TargetService::periodOf, Function.identity()));
		Map<MarketingPeriod, BigDecimal> computed = actuals.computed(type, from, to);
		boolean automatic = actuals.isAutomatic(type);
		List<TrendPoint> points = new ArrayList<>();
		for (List<MarketingPeriod> months : buckets) {
			List<BigDecimal> targetValues = new ArrayList<>();
			List<BigDecimal> actualValues = new ArrayList<>();
			int counted = 0;
			for (MarketingPeriod month : months) {
				MarketingTarget target = targets.get(month);
				if (target == null) {
					continue;
				}
				counted++;
				targetValues.add(target.getTargetValue());
				if (!TargetRules.isFuture(month, today)) {
					actualValues.add(TargetRules.resolve(target.getActualValue(), automatic, computed.get(month)).value());
				}
			}
			BigDecimal targetValue = TargetRules.combine(type.getUnit(), targetValues);
			boolean planned = TargetRules.isFuture(months.getFirst(), today);
			TargetProgress progress = targetValue == null || planned ? null
					: TargetProgress.of(targetValue, TargetRules.combine(type.getUnit(), actualValues), threshold);
			points.add(new TrendPoint(label(view, months), Period.of(months.getFirst()), Period.of(months.getLast()),
					counted, targetValue, progress == null ? null : progress.actual(),
					progress == null ? null : progress.achievementPct(), progress == null ? null : progress.remaining(),
					progress == null ? null : progress.status()));
		}
		return new TargetTrend(typeService.ref(type), view, threshold, points);
	}

	// --- changes ----------------------------------------------------------------------------------------------

	@Transactional
	public TargetItem create(CreateTarget request, AuthenticatedUser actor, ClientInfo client) {
		TargetType type = typeService.load(request.typeId());
		requireActive(type);
		MarketingPeriod period = new MarketingPeriod(request.month(), request.year());
		requireChangeable(period, actor);
		if (targetRepository.existsByTypeIdAndMonthAndYear(type.getId(), period.month(), period.year())) {
			throw alreadySet(type, period);
		}
		MarketingTarget target = new MarketingTarget();
		target.setType(type);
		target.setMonth(period.month());
		target.setYear(period.year());
		target.setTargetValue(money(TargetRules.requireValid(type.getUnit(), request.targetValue(), "target")));
		target.setActualValue(checkActual(type, period, null, request.actualValue()));
		target.setOwner(resolveOwner(request.ownerId()));
		target.setDepartment(resolveDepartment(request.departmentId()));
		target.setNotes(trimToNull(request.notes()));
		MarketingTarget saved = targetRepository.save(target);
		auditCreated(saved, actor, client);
		return toItems(List.of(saved), period, actor).getFirst();
	}

	@Transactional
	public TargetItem update(Long id, UpdateTarget request, AuthenticatedUser actor, ClientInfo client) {
		MarketingTarget target = load(id);
		MarketingPeriod period = periodOf(target);
		requireChangeable(period, actor);
		if (!Objects.equals(target.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this target just now. Reload and try again.");
		}
		TargetType type = target.getType();
		BigDecimal targetValue = money(TargetRules.requireValid(type.getUnit(), request.targetValue(), "target"));
		BigDecimal actualValue = checkActual(type, period, target.getActualValue(), request.actualValue());
		Department department = target.getDepartment().getId().equals(request.departmentId()) ? target.getDepartment()
				: resolveDepartment(request.departmentId());
		User owner = Objects.equals(userId(target.getOwner()), request.ownerId()) ? target.getOwner()
				: resolveOwner(request.ownerId());
		String notes = trimToNull(request.notes());
		AuditChanges changes = new AuditChanges().track("targetValue", target.getTargetValue(), targetValue)
			.track("actualValue", target.getActualValue(), actualValue)
			.track("ownerId", userId(target.getOwner()), userId(owner))
			.track("departmentId", target.getDepartment().getId(), department.getId())
			.track("notes", target.getNotes(), notes);
		target.setTargetValue(targetValue);
		target.setActualValue(actualValue);
		target.setDepartment(department);
		target.setOwner(owner);
		target.setNotes(notes);
		targetRepository.flush();
		if (!changes.isEmpty()) {
			Map<String, Object> details = new HashMap<>(changes.toDetails());
			details.put("typeCode", type.getCode());
			details.put("month", period.month());
			details.put("year", period.year());
			auditService.record(AuditAction.TARGET_UPDATED, actor.id(), ENTITY, id, details, client);
		}
		return toItems(List.of(target), period, actor).getFirst();
	}

	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		MarketingTarget target = load(id);
		MarketingPeriod period = periodOf(target);
		requireChangeable(period, actor);
		targetRepository.delete(target);
		auditService.record(AuditAction.TARGET_DELETED, actor.id(), ENTITY, id, Map.of("typeCode",
				target.getType().getCode(), "month", period.month(), "year", period.year(), "targetValue",
				target.getTargetValue()), client);
	}

	/** Sets several types' targets for one month, all or nothing. */
	@Transactional
	public SetMonthlyResult setMonthly(SetMonthlyTargets request, AuthenticatedUser actor, ClientInfo client) {
		MarketingPeriod period = new MarketingPeriod(request.month(), request.year());
		requireChangeable(period, actor);
		List<Long> typeIds = request.entries().stream().map(MonthlyTargetEntry::typeId).toList();
		if (new LinkedHashSet<>(typeIds).size() != typeIds.size()) {
			throw ApiException.badRequest("DUPLICATE_ENTRY", "Each target type can appear only once");
		}
		Map<Long, TargetType> types = typeRepository.findAllById(typeIds)
			.stream()
			.collect(Collectors.toMap(TargetType::getId, Function.identity()));
		if (types.size() != typeIds.size()) {
			throw ApiException.badRequest("UNKNOWN_TYPE", "One or more target types no longer exist. Reload and try again.");
		}
		types.values().forEach(TargetService::requireActive);
		List<Long> existing = targetRepository.findTypeIdsWithTarget(typeIds, period.month(), period.year());
		if (!existing.isEmpty()) {
			throw ApiException.conflict("TARGET_EXISTS", period.label() + " already has targets for "
					+ existing.stream().map(id -> types.get(id).getName()).sorted().collect(Collectors.joining(", "))
					+ ". Change those targets instead.");
		}
		User owner = resolveOwner(request.ownerId());
		Department department = resolveDepartment(request.departmentId());
		for (MonthlyTargetEntry entry : request.entries()) {
			TargetType type = types.get(entry.typeId());
			MarketingTarget target = new MarketingTarget();
			target.setType(type);
			target.setMonth(period.month());
			target.setYear(period.year());
			target.setTargetValue(money(TargetRules.requireValid(type.getUnit(), entry.targetValue(), "target")));
			target.setOwner(owner);
			target.setDepartment(department);
			auditCreated(targetRepository.save(target), actor, client);
		}
		return new SetMonthlyResult(Period.of(period), request.entries().size());
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private List<TargetItem> toItems(List<MarketingTarget> targets, MarketingPeriod period, AuthenticatedUser viewer) {
		LocalDate today = calendar.today();
		BigDecimal global = settings.marketingBehindThresholdPct();
		boolean future = TargetRules.isFuture(period, today);
		boolean canEdit = viewer.hasPermission(TARGET_EDIT)
				&& TargetRules.canChange(period, today, viewer.isSuperAdmin());
		// One aggregation per actual source in the month (types sharing a source share its query).
		Map<Long, BigDecimal> computed = future ? Map.of()
				: actuals.computedFor(targets.stream().map(MarketingTarget::getType).toList(), period);
		return targets.stream().map(target -> {
			TargetType type = target.getType();
			boolean automatic = actuals.isAutomatic(type);
			BigDecimal threshold = typeService.thresholdOf(type, global);
			ResolvedActual actual = future ? new ResolvedActual(null, TargetRules.ActualOrigin.NONE)
					: TargetRules.resolve(target.getActualValue(), automatic, computed.get(type.getId()));
			TargetProgress progress = future ? null : TargetProgress.of(target.getTargetValue(), actual.value(), threshold);
			return new TargetItem(target.getId(), typeService.ref(type), period.month(), period.year(), period.label(),
					target.getTargetValue(), actual.value(), actual.origin(),
					progress == null ? null : progress.achievementPct(),
					progress == null ? target.getTargetValue() : progress.remaining(),
					progress == null ? null : progress.status(), threshold, UserSummary.of(target.getOwner()),
					DepartmentSummary.of(target.getDepartment()), target.getNotes(), canEdit,
					canEdit && !future && !automatic, target.getVersion(), target.getCreatedAt(), target.getUpdatedAt());
		}).toList();
	}

	private static StatusSummary summarise(List<TargetItem> items) {
		int achieved = 0;
		int inProgress = 0;
		int behind = 0;
		for (TargetItem item : items) {
			if (item.status() == TargetStatus.ACHIEVED) {
				achieved++;
			}
			else if (item.status() == TargetStatus.IN_PROGRESS) {
				inProgress++;
			}
			else if (item.status() == TargetStatus.BEHIND) {
				behind++;
			}
		}
		return new StatusSummary(items.size(), achieved, inProgress, behind);
	}

	/**
	 * A hand-entered actual is only for types whose actual is not computed, and never for a future month. Clearing a
	 * kept value on an automatic type is allowed.
	 */
	private BigDecimal checkActual(TargetType type, MarketingPeriod period, BigDecimal current, BigDecimal requested) {
		BigDecimal actual = money(TargetRules.requireValid(type.getUnit(), requested, "actual"));
		if (actual == null || actual.equals(current)) {
			return actual;
		}
		if (actuals.isAutomatic(type)) {
			throw ApiException.badRequest("ACTUAL_IS_AUTOMATIC",
					"The actual for " + type.getName() + " is calculated automatically");
		}
		if (TargetRules.isFuture(period, calendar.today())) {
			throw ApiException.badRequest("FUTURE_ACTUAL", "Actuals cannot be entered for a future month");
		}
		return actual;
	}

	private void requireChangeable(MarketingPeriod period, AuthenticatedUser actor) {
		if (!TargetRules.canChange(period, calendar.today(), actor.isSuperAdmin())) {
			throw ApiException.conflict("MONTH_LOCKED", period.label()
					+ " is closed. Targets can be changed for the previous month onwards; history is kept as it was.");
		}
	}

	private static void requireActive(TargetType type) {
		if (!type.isActive()) {
			throw ApiException.badRequest("TYPE_INACTIVE", type.getName() + " is inactive");
		}
	}

	private static ApiException alreadySet(TargetType type, MarketingPeriod period) {
		return ApiException.conflict("TARGET_EXISTS",
				type.getName() + " already has a target for " + period.label() + ". Change that target instead.");
	}

	private void auditCreated(MarketingTarget target, AuthenticatedUser actor, ClientInfo client) {
		Map<String, Object> details = new HashMap<>();
		details.put("typeCode", target.getType().getCode());
		details.put("month", target.getMonth());
		details.put("year", target.getYear());
		details.put("targetValue", target.getTargetValue());
		details.put("actualValue", target.getActualValue());
		auditService.record(AuditAction.TARGET_CREATED, actor.id(), ENTITY, target.getId(), details, client);
	}

	private MarketingTarget load(Long id) {
		return targetRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("TARGET_NOT_FOUND", "Target not found"));
	}

	private Department resolveDepartment(Long departmentId) {
		Department department = departmentRepository.findById(departmentId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		if (department.getStatus() != DepartmentStatus.ACTIVE) {
			throw ApiException.badRequest("DEPARTMENT_INACTIVE", "Choose an active department");
		}
		return department;
	}

	/** Owners must be active Digital Marketing users. */
	private User resolveOwner(Long ownerId) {
		if (ownerId == null) {
			return null;
		}
		return userRepository.findActiveWithPermission(MarketingContextService.MARKETING_VIEW, UserStatus.ACTIVE)
			.stream()
			.filter(user -> user.getId().equals(ownerId))
			.findFirst()
			.orElseThrow(() -> ApiException.badRequest("INVALID_OWNER",
					"The owner must be an active user with access to Digital Marketing"));
	}

	/** Months of each trend bucket, oldest first. */
	static List<List<MarketingPeriod>> buckets(TrendView view, int year) {
		List<List<MarketingPeriod>> buckets = new ArrayList<>();
		switch (view) {
			case MONTH -> {
				for (int m = 1; m <= 12; m++) {
					buckets.add(List.of(new MarketingPeriod(m, year)));
				}
			}
			case QUARTER -> {
				for (int q = 0; q < 4; q++) {
					buckets.add(List.of(new MarketingPeriod(q * 3 + 1, year), new MarketingPeriod(q * 3 + 2, year),
							new MarketingPeriod(q * 3 + 3, year)));
				}
			}
			case YEAR -> {
				for (int y = year - TREND_YEARS + 1; y <= year; y++) {
					List<MarketingPeriod> months = new ArrayList<>();
					for (int m = 1; m <= 12; m++) {
						months.add(new MarketingPeriod(m, y));
					}
					buckets.add(months);
				}
			}
		}
		return buckets;
	}

	private static String label(TrendView view, List<MarketingPeriod> months) {
		MarketingPeriod first = months.getFirst();
		return switch (view) {
			case MONTH -> Month.of(first.month()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + first.year();
			case QUARTER -> "Q" + first.quarter() + " " + first.year();
			case YEAR -> String.valueOf(first.year());
		};
	}

	private static MarketingPeriod periodOf(MarketingTarget target) {
		return new MarketingPeriod(target.getMonth(), target.getYear());
	}

	private static int key(MarketingPeriod period) {
		return period.year() * 12 + period.month();
	}

	/** Values are stored with two decimals; normalising keeps "250" and "250.00" equal for audits. */
	private static BigDecimal money(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
	}

	private static Long userId(User user) {
		return user == null ? null : user.getId();
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
