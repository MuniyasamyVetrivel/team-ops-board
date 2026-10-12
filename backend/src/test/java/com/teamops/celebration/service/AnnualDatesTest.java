package com.teamops.celebration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Brief: birthdays and anniversaries repeat every year from the dates; 29 February falls on 28 February otherwise. */
class AnnualDatesTest {

	private static final LocalDate LEAP_BIRTHDAY = LocalDate.of(1996, 2, 29);

	@Test
	void february29thFallsOnThe28thInYearsWithoutIt() {
		assertThat(AnnualDates.in(LEAP_BIRTHDAY, 2027)).isEqualTo(LocalDate.of(2027, 2, 28));
		assertThat(AnnualDates.in(LEAP_BIRTHDAY, 2028)).isEqualTo(LocalDate.of(2028, 2, 29));
		assertThat(AnnualDates.in(LocalDate.of(1990, 10, 14), 2026)).isEqualTo(LocalDate.of(2026, 10, 14));
	}

	@Test
	void occurrencesInARangeCanCrossTheNewYear() {
		LocalDate born = LocalDate.of(1990, 1, 3);
		assertThat(AnnualDates.between(born, 1991, LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 10)))
			.containsExactly(LocalDate.of(2027, 1, 3));
		assertThat(AnnualDates.between(born, 1991, LocalDate.of(2026, 1, 4), LocalDate.of(2026, 12, 31))).isEmpty();
		assertThat(AnnualDates.between(LEAP_BIRTHDAY, 1997, LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 1)))
			.containsExactly(LocalDate.of(2027, 2, 28));
	}

	@Test
	void anniversariesStartAfterTheFirstFullYear() {
		LocalDate joined = LocalDate.of(2026, 10, 14);
		assertThat(AnnualDates.between(joined, AnnualDates.firstAnniversaryYear(joined), LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 10, 31))).as("the joining day itself is no anniversary").isEmpty();
		assertThat(AnnualDates.next(joined, AnnualDates.firstAnniversaryYear(joined), LocalDate.of(2026, 10, 20)))
			.isEqualTo(LocalDate.of(2027, 10, 14));
		assertThat(AnnualDates.years(LocalDate.of(2021, 10, 14), LocalDate.of(2026, 10, 14))).isEqualTo(5);
	}

	@Test
	void nextOccurrenceIsTodayOrLater() {
		LocalDate born = LocalDate.of(1990, 10, 14);
		assertThat(AnnualDates.next(born, 1991, LocalDate.of(2026, 10, 14))).isEqualTo(LocalDate.of(2026, 10, 14));
		assertThat(AnnualDates.next(born, 1991, LocalDate.of(2026, 10, 15))).isEqualTo(LocalDate.of(2027, 10, 14));
		assertThat(AnnualDates.next(LEAP_BIRTHDAY, 1997, LocalDate.of(2026, 3, 1))).isEqualTo(LocalDate.of(2027, 2, 28));
	}

}
