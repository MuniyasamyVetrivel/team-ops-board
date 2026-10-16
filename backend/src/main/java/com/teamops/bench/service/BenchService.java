package com.teamops.bench.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.bench.dto.BenchDtos.AllocationDto;
import com.teamops.bench.dto.BenchDtos.BenchDashboard;
import com.teamops.bench.dto.BenchDtos.BenchEntry;
import com.teamops.bench.dto.BenchDtos.ConsultantEmployee;
import com.teamops.bench.dto.BenchDtos.SkillCount;
import com.teamops.bench.dto.BenchDtos.StatusCount;
import com.teamops.bench.entity.Allocation;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.AllocationRepository;
import com.teamops.bench.repository.ConsultantRepository;
import com.teamops.bench.service.BenchMath.Period;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The bench and the Bench Management dashboard on today's business date, computed from every live consultant and
 * their allocations (two queries) and never stored:
 * <ul>
 * <li>Capacity: active consultants (HR status ACTIVE) who are available today (no availability date or one that has
 * passed) or already allocated. Leave of absence, notice period and left are outside capacity.</li>
 * <li>On the bench: in capacity and below 100% today, so partially allocated consultants are on it with what is left.
 * Bench start and days on bench follow {@link BenchMath#benchSince}.</li>
 * <li>Utilisation: allocated FTE ÷ capacity FTE, each consultant capped at 100% (over-allocation adds nothing);
 * billable counts first within that cap.</li>
 * <li>Roll-offs: allocations ending in the next 30 days.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenchService {

	public static final int ROLL_OFF_DAYS = 30;

	static final int DASHBOARD_LIST_LIMIT = 10;

	static final int SKILL_LIMIT = 20;

	private final ConsultantRepository consultants;

	private final AllocationRepository allocations;

	private final BusinessCalendar calendar;

	/** @param skill exact skill name, ignoring case */
	public record BenchFilter(String search, String skill) {
	}

	/** A consultant on a day: today's total and billable share, and whether they count towards capacity. */
	record Position(Consultant consultant, List<Period> periods, int allocated, int billable, boolean inCapacity) {

		ConsultantStatus status() {
			return ConsultantStatus.of(consultant.getEmploymentStatus(), allocated);
		}

		boolean onBench() {
			return inCapacity && allocated < BenchMath.FULL;
		}

	}

	/**
	 * The bench, longest on it first unless sorted otherwise ({@code daysOnBench}, {@code name},
	 * {@code allocatedPercent}).
	 */
	public PageResponse<BenchEntry> bench(BenchFilter filter, Pageable pageable) {
		LocalDate today = calendar.today();
		List<BenchEntry> entries = benchEntries(positions(today), today).stream()
			.filter(matches(filter))
			.sorted(comparator(pageable.getSort()))
			.toList();
		int from = (int) Math.min(pageable.getOffset(), entries.size());
		int to = Math.min(from + pageable.getPageSize(), entries.size());
		int pages = (entries.size() + pageable.getPageSize() - 1) / pageable.getPageSize();
		return new PageResponse<>(entries.subList(from, to), pageable.getPageNumber(), pageable.getPageSize(),
				entries.size(), pages);
	}

	public BenchDashboard dashboard() {
		LocalDate today = calendar.today();
		List<Position> positions = positions(today);

		Map<ConsultantStatus, Long> counts = new EnumMap<>(ConsultantStatus.class);
		positions.forEach(p -> counts.merge(p.status(), 1L, Long::sum));
		List<StatusCount> byStatus = new ArrayList<>();
		for (ConsultantStatus status : ConsultantStatus.values()) {
			byStatus.add(new StatusCount(status, counts.getOrDefault(status, 0L)));
		}

		long capacity = 0;
		long allocatedSum = 0;
		long billableSum = 0;
		for (Position position : positions) {
			if (position.inCapacity()) {
				int allocated = Math.min(position.allocated(), BenchMath.FULL);
				capacity++;
				allocatedSum += allocated;
				billableSum += Math.min(position.billable(), allocated);
			}
		}
		long nonBillableSum = allocatedSum - billableSum;
		long capacityPercent = capacity * BenchMath.FULL;

		List<BenchEntry> bench = benchEntries(positions, today);
		long unallocated = bench.stream().filter(e -> e.allocatedPercent() == 0).count();
		BigDecimal averageDays = bench.isEmpty() ? null
				: BigDecimal.valueOf(bench.stream().mapToLong(BenchEntry::daysOnBench).sum())
					.divide(BigDecimal.valueOf(bench.size()), 1, RoundingMode.HALF_UP);
		long longestDays = bench.stream().mapToLong(BenchEntry::daysOnBench).max().orElse(0);
		List<BenchEntry> longest = bench.stream()
			.sorted(comparator(Sort.by(Sort.Order.desc("daysOnBench"))))
			.limit(DASHBOARD_LIST_LIMIT)
			.toList();

		List<Allocation> ending = allocations.findByEndDateBetweenOrderByEndDateAscIdAsc(today,
				today.plusDays(ROLL_OFF_DAYS));
		List<AllocationDto> rollOffs = ending.stream()
			.limit(DASHBOARD_LIST_LIMIT)
			.map(a -> AllocationService.toDto(a, today))
			.toList();

		return new BenchDashboard(today, byStatus, capacity, fte(capacityPercent), fte(allocatedSum), fte(billableSum),
				fte(nonBillableSum), fte(capacityPercent - allocatedSum), percent(allocatedSum, capacityPercent),
				percent(billableSum, capacityPercent), percent(nonBillableSum, capacityPercent), bench.size(),
				unallocated, averageDays, longestDays, longest, ending.size(), rollOffs, skills(bench));
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	/** Every live consultant with their allocations and today's totals. */
	List<Position> positions(LocalDate today) {
		List<Consultant> all = consultants.findAllByOrderByIdAsc();
		Map<Long, List<Period>> periods = new LinkedHashMap<>();
		if (!all.isEmpty()) {
			allocations.findSlices(all.stream().map(Consultant::getId).toList())
				.forEach(slice -> periods.computeIfAbsent(slice.getConsultantId(), id -> new ArrayList<>())
					.add(AllocationService.period(slice)));
		}
		return all.stream().map(consultant -> {
			List<Period> own = periods.getOrDefault(consultant.getId(), List.of());
			int allocated = BenchMath.percentOn(own, today);
			boolean available = consultant.getAvailableFrom() == null || !consultant.getAvailableFrom().isAfter(today);
			boolean inCapacity = consultant.getEmploymentStatus() == EmploymentStatus.ACTIVE
					&& (available || allocated > 0);
			return new Position(consultant, own, allocated, BenchMath.billablePercentOn(own, today), inCapacity);
		}).toList();
	}

	private List<BenchEntry> benchEntries(List<Position> positions, LocalDate today) {
		return positions.stream().filter(Position::onBench).map(p -> {
			Consultant c = p.consultant();
			LocalDate added = c.getCreatedAt() == null ? null : LocalDate.ofInstant(c.getCreatedAt(), calendar.zone());
			LocalDate since = BenchMath.benchSince(p.periods(), today, c.getAvailableFrom(), added);
			return new BenchEntry(c.getId(), ConsultantEmployee.of(c.getEmployee()), c.getLocation(),
					ConsultantService.skillsOf(c), c.getAvailableFrom(), p.status(), p.allocated(),
					BenchMath.FULL - p.allocated(), since, ChronoUnit.DAYS.between(since, today));
		}).toList();
	}

	private static Predicate<BenchEntry> matches(BenchFilter filter) {
		String search = BenchText.trimToNull(filter.search()) == null ? null
				: filter.search().trim().toLowerCase(Locale.ROOT);
		String skill = BenchText.trimToNull(filter.skill()) == null ? null
				: filter.skill().trim().toLowerCase(Locale.ROOT);
		return entry -> {
			if (skill != null && entry.skills().stream().noneMatch(s -> s.toLowerCase(Locale.ROOT).equals(skill))) {
				return false;
			}
			if (search == null) {
				return true;
			}
			List<String> texts = new ArrayList<>(entry.skills());
			texts.add(entry.location());
			if (entry.employee() != null) {
				texts.add(entry.employee().fullName());
				texts.add(entry.employee().employeeCode());
			}
			return texts.stream().anyMatch(t -> t != null && t.toLowerCase(Locale.ROOT).contains(search));
		};
	}

	/** In-memory sort for the whitelisted properties, then by consultant. */
	static Comparator<BenchEntry> comparator(Sort sort) {
		Comparator<BenchEntry> result = null;
		for (Sort.Order order : sort) {
			Comparator<BenchEntry> next = switch (order.getProperty()) {
				case "daysOnBench" -> Comparator.comparingLong(BenchEntry::daysOnBench);
				case "allocatedPercent" -> Comparator.comparingInt(BenchEntry::allocatedPercent);
				case "name" -> Comparator.comparing((BenchEntry e) -> e.employee() == null ? null : e.employee().fullName(),
						Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
				default -> throw new IllegalArgumentException("Unsupported bench sort: " + order.getProperty());
			};
			next = order.isDescending() ? next.reversed() : next;
			result = result == null ? next : result.thenComparing(next);
		}
		Comparator<BenchEntry> byId = Comparator.comparing(BenchEntry::consultantId);
		return result == null ? byId : result.thenComparing(byId);
	}

	/** Skills on the bench, most common first, each counted once per consultant and spelled as first seen. */
	private static List<SkillCount> skills(List<BenchEntry> bench) {
		Map<String, String> spelling = new LinkedHashMap<>();
		Map<String, Long> counts = bench.stream()
			.flatMap(entry -> entry.skills().stream().map(skill -> {
				String key = BenchText.key(skill);
				spelling.putIfAbsent(key, skill);
				return key;
			}).distinct())
			.collect(Collectors.groupingBy(key -> key, LinkedHashMap::new, Collectors.counting()));
		return counts.entrySet()
			.stream()
			.map(entry -> new SkillCount(spelling.get(entry.getKey()), entry.getValue()))
			.sorted(Comparator.comparingLong(SkillCount::consultants)
				.reversed()
				.thenComparing(SkillCount::skill, String.CASE_INSENSITIVE_ORDER))
			.limit(SKILL_LIMIT)
			.toList();
	}

	/** Percentage points as FTE: 250 → 2.50. */
	private static BigDecimal fte(long percentPoints) {
		return BigDecimal.valueOf(percentPoints).divide(BigDecimal.valueOf(BenchMath.FULL), 2, RoundingMode.HALF_UP);
	}

	/** part ÷ whole × 100 to one decimal; null when there is nothing to divide by. */
	static BigDecimal percent(long part, long whole) {
		return whole == 0 ? null
				: BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
	}

}
