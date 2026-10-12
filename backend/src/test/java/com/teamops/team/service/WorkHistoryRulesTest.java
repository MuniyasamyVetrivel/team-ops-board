package com.teamops.team.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.team.dto.WorkHistoryDtos.DatePreset;
import com.teamops.team.dto.WorkHistoryDtos.WeekCount;

class WorkHistoryRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

	@Test
	void onTimeMeansCompletedOnOrBeforeTheDueDate() {
		assertThat(WorkHistoryRules.onTime(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20))).isTrue();
		assertThat(WorkHistoryRules.onTime(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 20))).isFalse();
		assertThat(WorkHistoryRules.onTime(LocalDate.of(2026, 9, 23), null)).as("no due date").isNull();
	}

	@Test
	void daysLateForLateCompletionsAndOverdueWork() {
		assertThat(WorkHistoryRules.daysLate(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 23), TODAY)).isEqualTo(3);
		assertThat(WorkHistoryRules.daysLate(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19), TODAY)).isNull();
		assertThat(WorkHistoryRules.daysLate(LocalDate.of(2026, 10, 10), null, TODAY)).as("open, overdue").isEqualTo(2);
		assertThat(WorkHistoryRules.daysLate(LocalDate.of(2026, 10, 15), null, TODAY)).isNull();
		assertThat(WorkHistoryRules.daysLate(null, LocalDate.of(2026, 9, 19), TODAY)).isNull();
	}

	@Test
	void daysToCompleteCountFromTheStartOrCreation() {
		LocalDate created = LocalDate.of(2026, 9, 1);
		assertThat(WorkHistoryRules.daysToComplete(LocalDate.of(2026, 9, 10), created, LocalDate.of(2026, 9, 15))).isEqualTo(5);
		assertThat(WorkHistoryRules.daysToComplete(null, created, LocalDate.of(2026, 9, 15))).isEqualTo(14);
		assertThat(WorkHistoryRules.daysToComplete(LocalDate.of(2026, 9, 20), created, LocalDate.of(2026, 9, 15)))
			.as("started after completion: from creation").isEqualTo(14);
		assertThat(WorkHistoryRules.daysToComplete(null, LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 15))).isZero();
	}

	@Test
	void percentagesAndAveragesAreNullWithNothingToDivide() {
		assertThat(WorkHistoryRules.percent(2, 3)).isEqualByComparingTo(new BigDecimal("66.7"));
		assertThat(WorkHistoryRules.percent(0, 0)).isNull();
		assertThat(WorkHistoryRules.average(List.of(2L, 3L, 3L))).isEqualByComparingTo(new BigDecimal("2.7"));
		assertThat(WorkHistoryRules.average(List.of())).isNull();
	}

	@Test
	void weeksCoverTheRangeOrTheLastTwelveWeeks() {
		// 10 to 20 September 2026: Thursday to Sunday of the next week.
		assertThat(WorkHistoryRules.weeks(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20), TODAY))
			.containsExactly(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14));
		List<LocalDate> recent = WorkHistoryRules.weeks(null, null, TODAY);
		assertThat(recent).hasSize(12).endsWith(LocalDate.of(2026, 10, 12));
		assertThat(WorkHistoryRules.weeks(LocalDate.of(2020, 1, 1), TODAY, TODAY)).hasSize(WorkHistoryRules.MAX_WEEKS)
			.endsWith(LocalDate.of(2026, 10, 12));
	}

	@Test
	void completionsAreCountedPerWeekWithinTheRange() {
		List<LocalDate> weeks = List.of(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14));
		assertThat(WorkHistoryRules.perWeek(weeks, List.of(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13),
				LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 1))))
			.containsExactly(new WeekCount(LocalDate.of(2026, 9, 7), 2), new WeekCount(LocalDate.of(2026, 9, 14), 1));
	}

	@Test
	void presetsResolveAgainstTheBusinessToday() {
		// Monday 12 October 2026.
		assertThat(range(DatePreset.THIS_WEEK)).containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));
		assertThat(range(DatePreset.LAST_WEEK)).containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));
		assertThat(range(DatePreset.THIS_MONTH)).containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
		assertThat(range(DatePreset.LAST_MONTH)).containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
		assertThat(range(DatePreset.LAST_3_MONTHS)).containsExactly(LocalDate.of(2026, 7, 13), TODAY);
		assertThat(range(DatePreset.LAST_30_DAYS)).containsExactly(LocalDate.of(2026, 9, 13), TODAY);
		WorkHistoryService.Filter custom = new WorkHistoryService.Filter(null, null, null, LocalDate.of(2026, 9, 10),
				LocalDate.of(2026, 9, 20), null, null, null);
		assertThat(custom.resolve(TODAY)).isSameAs(custom);
	}

	private static List<LocalDate> range(DatePreset preset) {
		WorkHistoryService.Filter resolved = new WorkHistoryService.Filter(null, null, preset, null, null, null, null, null)
			.resolve(TODAY);
		return List.of(resolved.from(), resolved.to());
	}

	@Test
	void exportShowsTimelinessInWords() {
		assertThat(WorkHistoryService.timeliness(true, null, false)).isEqualTo("Yes");
		assertThat(WorkHistoryService.timeliness(false, 1, false)).isEqualTo("Late by 1 day");
		assertThat(WorkHistoryService.timeliness(null, 3, true)).isEqualTo("Overdue by 3 days");
		assertThat(WorkHistoryService.timeliness(null, null, false)).isEmpty();
	}

}
