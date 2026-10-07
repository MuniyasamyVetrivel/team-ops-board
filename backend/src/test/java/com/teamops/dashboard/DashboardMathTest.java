package com.teamops.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.teamops.dashboard.DashboardDtos.WeekPoint;
import com.teamops.dashboard.DashboardMath.WeekCounts;

class DashboardMathTest {

	@Test
	void percentRoundsHalfUpAndDivisionByZeroIsNull() {
		assertThat(DashboardMath.percent(1, 3)).isEqualTo(33);
		assertThat(DashboardMath.percent(2, 3)).isEqualTo(67);
		assertThat(DashboardMath.percent(1, 8)).as("12.5 rounds up").isEqualTo(13);
		assertThat(DashboardMath.percent(5, 4)).isEqualTo(125);
		assertThat(DashboardMath.percent(0, 0)).isNull();
		assertThat(DashboardMath.percent(new BigDecimal("60"), new BigDecimal("80"))).isEqualTo(75);
		assertThat(DashboardMath.percent(null, new BigDecimal("80"))).isZero();
		assertThat(DashboardMath.percent(new BigDecimal("5"), BigDecimal.ZERO)).isNull();
		assertThat(DashboardMath.percent(new BigDecimal("5"), null)).isNull();
	}

	@Test
	void weeklySeriesIsOldestFirstAndFillsGaps() {
		LocalDate thisWeek = LocalDate.of(2026, 10, 5); // a Monday
		Map<LocalDate, WeekCounts> counts = Map.of(thisWeek, new WeekCounts(4, 4, 3),
				thisWeek.minusWeeks(2), new WeekCounts(2, 0, 0));

		List<WeekPoint> series = DashboardMath.weeklySeries(thisWeek, 4, counts);

		assertThat(series).extracting(WeekPoint::weekStart)
			.containsExactly(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28),
					thisWeek);
		assertThat(series).extracting(WeekPoint::completed).containsExactly(0L, 2L, 0L, 4L);
		assertThat(series.get(3).onTimePercent()).isEqualTo(75);
		assertThat(series.get(1).onTimePercent()).as("no due dates: no on-time rate").isNull();
		assertThat(series.get(0).onTimePercent()).isNull();
	}

}
