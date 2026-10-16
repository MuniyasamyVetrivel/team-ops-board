package com.teamops.bench.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Allocation arithmetic over a consultant's allocations, all dates inclusive and an open end ({@code null}) running
 * for ever. Pure functions, so the rules are unit-tested without a database:
 * <ul>
 * <li>A consultant's percentage on a day is the sum of the allocations covering it.</li>
 * <li>Over-allocated: any stretch where that sum goes above 100% (a warning, never a block).</li>
 * <li>On the bench: below 100% today. The bench started the day after the last day at 100% or more, but not before the
 * availability date; without either, on the fallback date (when the consultant was added).</li>
 * </ul>
 */
public final class BenchMath {

	public static final int FULL = 100;

	/** One allocation as the arithmetic sees it. */
	public record Period(LocalDate start, LocalDate end, int percent, boolean billable) {

		public boolean covers(LocalDate day) {
			return !day.isBefore(start) && (end == null || !day.isAfter(end));
		}

		boolean overlaps(LocalDate from, LocalDate to) {
			return (to == null || !start.isAfter(to)) && (end == null || !end.isBefore(from));
		}

	}

	/** Days {@code from} to {@code to} (null: open-ended) with the same total percentage. */
	public record Segment(LocalDate from, LocalDate to, int totalPercent) {

	}

	private BenchMath() {
	}

	public static int percentOn(Collection<Period> periods, LocalDate day) {
		return periods.stream().filter(p -> p.covers(day)).mapToInt(Period::percent).sum();
	}

	public static int billablePercentOn(Collection<Period> periods, LocalDate day) {
		return periods.stream().filter(p -> p.billable() && p.covers(day)).mapToInt(Period::percent).sum();
	}

	/**
	 * The total percentage over time, from the first allocation's start: consecutive stretches with the same total
	 * (0 between allocations), the last one open-ended when an open allocation remains.
	 */
	public static List<Segment> timeline(Collection<Period> periods) {
		TreeMap<LocalDate, Integer> changes = new TreeMap<>();
		for (Period period : periods) {
			changes.merge(period.start(), period.percent(), Integer::sum);
			if (period.end() != null) {
				changes.merge(period.end().plusDays(1), -period.percent(), Integer::sum);
			}
		}
		List<Segment> segments = new ArrayList<>();
		int total = 0;
		Map.Entry<LocalDate, Integer> change = changes.firstEntry();
		while (change != null) {
			total += change.getValue();
			Map.Entry<LocalDate, Integer> next = changes.higherEntry(change.getKey());
			LocalDate to = next == null ? null : next.getKey().minusDays(1);
			if (next != null || total != 0) {
				Segment last = segments.isEmpty() ? null : segments.getLast();
				if (last != null && last.totalPercent() == total) {
					segments.set(segments.size() - 1, new Segment(last.from(), to, total));
				}
				else {
					segments.add(new Segment(change.getKey(), to, total));
				}
			}
			change = next;
		}
		return segments;
	}

	/** The stretches above 100% that touch {@code from}–{@code to} (null: open-ended). */
	public static List<Segment> overAllocated(Collection<Period> periods, LocalDate from, LocalDate to) {
		Period window = new Period(from, to, 0, false);
		return timeline(periods).stream()
			.filter(s -> s.totalPercent() > FULL)
			.filter(s -> window.overlaps(s.from(), s.to()))
			.toList();
	}

	/**
	 * When the consultant's current stretch on the bench started (they are below 100% today): the day after the last
	 * day at 100% or more, not before {@code availableFrom}, else {@code fallback}; never after today.
	 */
	public static LocalDate benchSince(Collection<Period> periods, LocalDate today, LocalDate availableFrom,
			LocalDate fallback) {
		LocalDate yesterday = today.minusDays(1);
		LocalDate afterLastFull = null;
		for (Segment segment : timeline(periods)) {
			if (segment.totalPercent() >= FULL && !segment.from().isAfter(yesterday)) {
				LocalDate end = segment.to() == null || segment.to().isAfter(yesterday) ? yesterday : segment.to();
				afterLastFull = end.plusDays(1);
			}
		}
		LocalDate since = latest(afterLastFull, availableFrom);
		if (since == null) {
			since = fallback;
		}
		return since == null || since.isAfter(today) ? today : since;
	}

	private static LocalDate latest(LocalDate a, LocalDate b) {
		if (a == null) {
			return b;
		}
		return b == null || a.isAfter(b) ? a : b;
	}

}
