package com.teamops.bench.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.bench.service.BenchMath.Period;
import com.teamops.bench.service.BenchMath.Segment;

/** Allocation arithmetic: totals per day, over-allocation warnings and when a stretch on the bench started. */
class BenchMathTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

	@Test
	void aDaysTotalAddsTheAllocationsCoveringItWithBothEndsIncluded() {
		List<Period> periods = List.of(period("2026-10-01", "2026-10-16", 50, true),
				period("2026-10-16", null, 30, false), period("2026-10-17", "2026-12-31", 40, true));

		assertThat(BenchMath.percentOn(periods, TODAY)).isEqualTo(80);
		assertThat(BenchMath.billablePercentOn(periods, TODAY)).isEqualTo(50);
		assertThat(BenchMath.percentOn(periods, TODAY.plusDays(1))).isEqualTo(70);
		assertThat(BenchMath.percentOn(periods, LocalDate.of(2026, 9, 30))).isZero();
	}

	@Test
	void theTimelineMergesEqualStretchesAndEndsOpenWhileAnAllocationRunsOn() {
		List<Segment> timeline = BenchMath.timeline(List.of(period("2026-10-01", "2026-10-10", 50, true),
				period("2026-10-11", "2026-10-20", 50, true), period("2026-11-01", null, 100, true)));

		assertThat(timeline).containsExactly(new Segment(date("2026-10-01"), date("2026-10-20"), 50),
				new Segment(date("2026-10-21"), date("2026-10-31"), 0), new Segment(date("2026-11-01"), null, 100));
		assertThat(BenchMath.timeline(List.of())).isEmpty();
	}

	@Test
	void overAllocationIsEveryStretchAbove100PercentTouchingTheAllocation() {
		Period existing = period("2026-10-01", "2026-12-31", 60, true);
		Period other = period("2027-01-01", "2027-01-31", 70, true);
		Period added = period("2026-12-01", "2027-01-10", 50, true);

		List<Segment> over = BenchMath.overAllocated(List.of(existing, other, added), added.start(), added.end());

		assertThat(over).containsExactly(new Segment(date("2026-12-01"), date("2026-12-31"), 110),
				new Segment(date("2027-01-01"), date("2027-01-10"), 120));
		assertThat(BenchMath.overAllocated(List.of(existing, period("2026-10-01", "2026-12-31", 40, false)),
				date("2026-10-01"), date("2026-12-31"))).as("exactly 100% is fine").isEmpty();
		assertThat(BenchMath.overAllocated(List.of(existing, other, added), date("2027-02-01"), null))
			.as("only stretches the allocation touches").isEmpty();
	}

	@Test
	void theBenchStartsTheDayAfterTheLastFullDayButNotBeforeAvailability() {
		List<Period> rolledOff = List.of(period("2026-06-01", "2026-09-30", 100, true),
				period("2026-10-01", null, 40, true));
		assertThat(BenchMath.benchSince(rolledOff, TODAY, null, date("2026-01-01"))).isEqualTo(date("2026-10-01"));
		assertThat(BenchMath.benchSince(rolledOff, TODAY, date("2026-10-05"), null)).isEqualTo(date("2026-10-05"));

		// Two allocations adding up to 100% count as full.
		List<Period> split = List.of(period("2026-09-01", "2026-10-09", 60, true),
				period("2026-09-15", "2026-10-09", 40, false));
		assertThat(BenchMath.benchSince(split, TODAY, null, null)).isEqualTo(date("2026-10-10"));
	}

	@Test
	void withoutAFullDayTheBenchStartsOnAvailabilityOrWhenTheConsultantWasAdded() {
		List<Period> partial = List.of(period("2026-01-01", null, 50, true));
		assertThat(BenchMath.benchSince(partial, TODAY, date("2026-03-01"), date("2026-02-01")))
			.isEqualTo(date("2026-03-01"));
		assertThat(BenchMath.benchSince(List.of(), TODAY, null, date("2026-02-01"))).isEqualTo(date("2026-02-01"));
		assertThat(BenchMath.benchSince(List.of(), TODAY, null, null)).isEqualTo(TODAY);
		assertThat(BenchMath.benchSince(List.of(), TODAY, TODAY.plusDays(3), null)).as("never after today")
			.isEqualTo(TODAY);
	}

	private static Period period(String start, String end, int percent, boolean billable) {
		return new Period(date(start), end == null ? null : date(end), percent, billable);
	}

	private static LocalDate date(String text) {
		return LocalDate.parse(text);
	}

}
