package com.teamops.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.report.dto.ReportDtos.Range;

/** The date range and ageing rules of the management reports. */
class ReportServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

	private ReportService service() {
		BusinessCalendar calendar = mock(BusinessCalendar.class);
		when(calendar.today()).thenReturn(TODAY);
		return new ReportService(null, null, null, null, null, null, calendar);
	}

	@Test
	void theRangeDefaultsToTheLast30DaysAndIsCheckedForOrderAndLength() {
		ReportService service = service();
		assertThat(service.range(null, null)).isEqualTo(new Range(LocalDate.of(2026, 9, 10), TODAY));
		assertThat(service.range(LocalDate.of(2026, 1, 1), null)).isEqualTo(new Range(LocalDate.of(2026, 1, 1), TODAY));
		assertThat(service.range(null, LocalDate.of(2026, 6, 30))).isEqualTo(new Range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
		assertThatThrownBy(() -> service.range(TODAY, TODAY.minusDays(1))).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo("INVALID_RANGE");
		assertThat(service.range(TODAY.minusDays(365), TODAY).from()).isEqualTo(TODAY.minusDays(365));
		assertThatThrownBy(() -> service.range(TODAY.minusDays(366), TODAY)).isInstanceOf(ApiException.class);
	}

	@Test
	void openTicketsAreAgedInFiveBuckets() {
		assertThat(ReportService.ageBucket(0)).isZero();
		assertThat(ReportService.ageBucket(23)).isZero();
		assertThat(ReportService.ageBucket(24)).isEqualTo(1);
		assertThat(ReportService.ageBucket(71)).isEqualTo(1);
		assertThat(ReportService.ageBucket(72)).isEqualTo(2);
		assertThat(ReportService.ageBucket(168)).isEqualTo(3);
		assertThat(ReportService.ageBucket(719)).isEqualTo(3);
		assertThat(ReportService.ageBucket(720)).isEqualTo(4);
		assertThat(ReportService.AGE_LABELS).hasSize(5);
	}

}
