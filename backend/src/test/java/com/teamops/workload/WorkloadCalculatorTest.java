package com.teamops.workload;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.UserStatus;
import com.teamops.workload.WorkloadDtos.Row;

class WorkloadCalculatorTest {

	private static final BigDecimal FORTY = new BigDecimal("40");

	@Test
	void capacityScalesWeeklyHoursToTheWindow() {
		assertThat(WorkloadCalculator.capacityHours(FORTY, 14)).isEqualByComparingTo("80");
		assertThat(WorkloadCalculator.capacityHours(new BigDecimal("32"), 7)).isEqualByComparingTo("32");
	}

	@ParameterizedTest(name = "{0} remaining hours of 80 -> {1}%")
	@CsvSource({ "0, 0", "32, 40", "32.8, 41", "56, 70", "57, 71", "80, 100", "80.8, 101", "96, 120" })
	void percentIsRemainingOverCapacity(BigDecimal remaining, int expected) {
		assertThat(WorkloadCalculator.percent(remaining, FORTY, 14)).isEqualTo(expected);
	}

	@Test
	void zeroCapacityNeverDividesByZero() {
		assertThat(WorkloadCalculator.percent(BigDecimal.TEN, BigDecimal.ZERO, 14)).isZero();
	}

	@ParameterizedTest(name = "{0}% -> {1}")
	@CsvSource({ "0, LOW", "40, LOW", "41, NORMAL", "70, NORMAL", "71, HIGH", "100, HIGH", "101, OVERLOADED",
			"250, OVERLOADED" })
	void levelsFollowTheBriefBoundaries(int percent, WorkloadLevel expected) {
		assertThat(WorkloadLevel.of(percent)).isEqualTo(expected);
	}

	@Test
	void remainingHoursUsesEstimateMinusActualOrTheDefault() {
		BigDecimal defaultHours = new BigDecimal("4");
		assertThat(WorkloadCalculator.remainingHours(new BigDecimal("10"), new BigDecimal("3"), defaultHours))
			.isEqualByComparingTo("7");
		assertThat(WorkloadCalculator.remainingHours(new BigDecimal("5"), new BigDecimal("8"), defaultHours))
			.as("over-run tasks count as zero, not negative")
			.isEqualByComparingTo("0");
		assertThat(WorkloadCalculator.remainingHours(null, null, defaultHours)).isEqualByComparingTo("4");
	}

	@Test
	void sortsAndSummarises() {
		Row light = row("Asha", 20, 1, 5);
		Row heavy = row("Bala", 120, 4, 1);
		Row normal = row("Chitra", 55, 0, 9);

		assertThat(List.of(light, heavy, normal).stream().sorted(WorkloadService.comparator(WorkloadDtos.Sort.HIGHEST)))
			.containsExactly(heavy, normal, light);
		assertThat(List.of(light, heavy, normal).stream()
			.sorted(WorkloadService.comparator(WorkloadDtos.Sort.MOST_COMPLETED))).containsExactly(normal, light, heavy);

		WorkloadDtos.Summary summary = WorkloadService.summarise(List.of(light, heavy, normal));
		assertThat(summary.people()).isEqualTo(3);
		assertThat(summary.byLevel()).containsEntry(WorkloadLevel.OVERLOADED, 1L)
			.containsEntry(WorkloadLevel.LOW, 1L)
			.containsEntry(WorkloadLevel.NORMAL, 1L)
			.containsEntry(WorkloadLevel.HIGH, 0L);
		assertThat(summary.overdue()).isEqualTo(5);
		assertThat(summary.averagePercent()).isEqualTo(65);
	}

	private static Row row(String name, int percent, long overdue, long completed) {
		return new Row(new UserSummary((long) name.hashCode(), name, name + "@teamops.local", null, UserStatus.ACTIVE),
				new DepartmentSummary(1L, "IT", "IT"), 5, 1, 1, 0, 0, completed, overdue, 0, 3, BigDecimal.ONE,
				new BigDecimal("80"), percent, WorkloadLevel.of(percent));
	}

}
