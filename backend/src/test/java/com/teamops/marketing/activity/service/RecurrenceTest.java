package com.teamops.marketing.activity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.teamops.marketing.activity.entity.Frequency;
import com.teamops.marketing.activity.service.Recurrence.Period;

/** Occurrence periods and next-occurrence generation for every frequency (brief section 35). */
class RecurrenceTest {

	private static Period period(String start, String end) {
		return new Period(LocalDate.parse(start), LocalDate.parse(end));
	}

	@ParameterizedTest(name = "{0}: {1} is in {2}..{3}, next {4}..{5}")
	@CsvSource({
			// Daily: the day itself, next day across a month end.
			"DAILY,     2026-10-31, 2026-10-31, 2026-10-31, 2026-11-01, 2026-11-01",
			// Weekly: ISO weeks (Monday to Sunday), across the year end.
			"WEEKLY,    2026-12-31, 2026-12-28, 2027-01-03, 2027-01-04, 2027-01-10",
			"WEEKLY,    2026-10-05, 2026-10-05, 2026-10-11, 2026-10-12, 2026-10-18",
			// Monthly: October 1 completed → November 1 (the brief's example); February in a leap year.
			"MONTHLY,   2026-10-01, 2026-10-01, 2026-10-31, 2026-11-01, 2026-11-30",
			"MONTHLY,   2028-01-15, 2028-01-01, 2028-01-31, 2028-02-01, 2028-02-29",
			// Quarterly: Q4 → Q1 of the next year.
			"QUARTERLY, 2026-11-20, 2026-10-01, 2026-12-31, 2027-01-01, 2027-03-31",
			"QUARTERLY, 2026-02-03, 2026-01-01, 2026-03-31, 2026-04-01, 2026-06-30",
			// Yearly.
			"YEARLY,    2026-07-04, 2026-01-01, 2026-12-31, 2027-01-01, 2027-12-31" })
	void periodsAndTheNextOccurrence(Frequency frequency, LocalDate date, LocalDate start, LocalDate end,
			LocalDate nextStart, LocalDate nextEnd) {
		Period period = Recurrence.containing(frequency, date);
		assertThat(period).isEqualTo(new Period(start, end));
		assertThat(period.contains(date)).isTrue();
		assertThat(Recurrence.next(frequency, period)).isEqualTo(new Period(nextStart, nextEnd));
	}

	@Test
	void theDueDateIsOffsetFromThePeriodStartButStaysInsideIt() {
		// The brief: the October ranking update is due October 5.
		assertThat(Recurrence.dueDate(period("2026-10-01", "2026-10-31"), 4)).isEqualTo(LocalDate.of(2026, 10, 5));
		assertThat(Recurrence.dueDate(period("2026-10-05", "2026-10-11"), 30)).isEqualTo(LocalDate.of(2026, 10, 11));
		assertThat(Recurrence.dueDate(period("2026-02-01", "2026-02-28"), 0)).isEqualTo(LocalDate.of(2026, 2, 1));
	}

	@Test
	void anActivityCoversPeriodsThatOverlapItsDates() {
		LocalDate start = LocalDate.of(2026, 10, 15);
		LocalDate end = LocalDate.of(2026, 12, 10);
		assertThat(Recurrence.covers(period("2026-10-01", "2026-10-31"), start, end)).isTrue();
		assertThat(Recurrence.covers(period("2026-12-01", "2026-12-31"), start, end)).isTrue();
		assertThat(Recurrence.covers(period("2026-09-01", "2026-09-30"), start, end)).isFalse();
		assertThat(Recurrence.covers(period("2027-01-01", "2027-01-31"), start, end)).isFalse();
		assertThat(Recurrence.covers(period("2030-01-01", "2030-01-31"), start, null)).isTrue();
	}

	@Test
	void taskTitlesFillInThePeriod() {
		Period october = period("2026-10-01", "2026-10-31");
		// The brief: "Monthly SEO Ranking Update" creates "Update October keyword rankings".
		assertThat(Recurrence.title("Update {month} keyword rankings", Frequency.MONTHLY, october))
			.isEqualTo("Update October keyword rankings");
		assertThat(Recurrence.title("{quarter} {year} SEO audit", Frequency.QUARTERLY, period("2026-10-01", "2026-12-31")))
			.isEqualTo("Q4 2026 SEO audit");
		assertThat(Recurrence.title("Blog ({period}, {week})", Frequency.WEEKLY, period("2026-10-05", "2026-10-11")))
			.isEqualTo("Blog (Week of 5 Oct 2026, Week 41)");
		assertThat(Recurrence.title("Daily check {date} {unknown}", Frequency.DAILY, period("2026-10-08", "2026-10-08")))
			.isEqualTo("Daily check 8 Oct 2026 {unknown}");
	}

	@Test
	void labelsPerFrequency() {
		assertThat(Recurrence.label(Frequency.MONTHLY, period("2026-10-01", "2026-10-31"))).isEqualTo("October 2026");
		assertThat(Recurrence.label(Frequency.YEARLY, period("2026-01-01", "2026-12-31"))).isEqualTo("2026");
		assertThat(Recurrence.label(Frequency.DAILY, period("2026-10-08", "2026-10-08"))).isEqualTo("8 Oct 2026");
	}

}
