package com.teamops.vendor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.vendor.entity.VendorContract;
import com.teamops.vendor.service.ContractRules.ContractStatus;

/** Contract status, renewal date, notice deadline and reminder lead times are computed, never stored. */
class ContractRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

	@Test
	void statusFollowsTheDatesWithBothEndsIncluded() {
		assertThat(ContractStatus.of(TODAY.plusDays(1), null, TODAY)).isEqualTo(ContractStatus.UPCOMING);
		assertThat(ContractStatus.of(TODAY, TODAY, TODAY)).isEqualTo(ContractStatus.ACTIVE);
		assertThat(ContractStatus.of(TODAY.minusYears(1), null, TODAY)).isEqualTo(ContractStatus.ACTIVE);
		assertThat(ContractStatus.of(TODAY.minusYears(1), TODAY.minusDays(1), TODAY)).isEqualTo(ContractStatus.EXPIRED);
	}

	@Test
	void aContractComesUpForRenewalOnItsRenewalDateOrElseItsEndDate() {
		assertThat(ContractRules.renewsOn(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 2, 28)))
			.isEqualTo(LocalDate.of(2027, 3, 1));
		assertThat(ContractRules.renewsOn(null, LocalDate.of(2027, 2, 28))).isEqualTo(LocalDate.of(2027, 2, 28));
		assertThat(ContractRules.renewsOn(null, null)).isNull();
	}

	@Test
	void noticeIsDueTheNoticePeriodBeforeTheRenewal() {
		assertThat(ContractRules.noticeBy(LocalDate.of(2027, 3, 1), 30)).isEqualTo(LocalDate.of(2027, 1, 30));
		assertThat(ContractRules.noticeBy(LocalDate.of(2027, 3, 1), 0)).isEqualTo(LocalDate.of(2027, 3, 1));
		assertThat(ContractRules.noticeBy(LocalDate.of(2027, 3, 1), null)).isNull();
		assertThat(ContractRules.noticeBy(null, 30)).isNull();
	}

	@Test
	void daysUntilRenewalOnlyWhileThereIsSomethingToDecide() {
		assertThat(ContractRules.daysUntilRenewal(contract("2026-01-01", "2026-12-31", null), TODAY)).isEqualTo(75);
		assertThat(ContractRules.daysUntilRenewal(contract("2026-01-01", null, "2026-10-17"), TODAY)).isZero();
		// Renewal date already passed, expired, or nothing to renew.
		assertThat(ContractRules.daysUntilRenewal(contract("2026-01-01", null, "2026-10-16"), TODAY)).isNull();
		assertThat(ContractRules.daysUntilRenewal(contract("2025-01-01", "2026-10-16", "2026-11-01"), TODAY)).isNull();
		assertThat(ContractRules.daysUntilRenewal(contract("2026-01-01", null, null), TODAY)).isNull();
	}

	@Test
	void theReminderDueIsTheShortestLeadTimeTheRenewalIsWithin() {
		assertThat(ContractRules.leadFor(30)).isEqualTo(30);
		assertThat(ContractRules.leadFor(8)).isEqualTo(30);
		assertThat(ContractRules.leadFor(7)).isEqualTo(7);
		assertThat(ContractRules.leadFor(0)).isEqualTo(7);
	}

	@Test
	void endAndRenewalDatesMayNotComeBeforeTheStart() {
		LocalDate start = LocalDate.of(2026, 3, 1);
		ContractRules.checkDates(start, start, start);
		ContractRules.checkDates(start, null, null);
		assertThatThrownBy(() -> ContractRules.checkDates(start, start.minusDays(1), null)).isInstanceOf(ApiException.class)
			.hasMessageContaining("end date");
		assertThatThrownBy(() -> ContractRules.checkDates(start, null, start.minusDays(1)))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("renewal date");
	}

	static VendorContract contract(String start, String end, String renewal) {
		VendorContract contract = new VendorContract();
		contract.setStartDate(LocalDate.parse(start));
		contract.setEndDate(end == null ? null : LocalDate.parse(end));
		contract.setRenewalDate(renewal == null ? null : LocalDate.parse(renewal));
		return contract;
	}

}
