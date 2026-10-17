package com.teamops.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.LicenceStatus;

/** Seats, utilisation, under-use, annual cost, renewal days and payment labels are computed or checked, never stored. */
class LicenceRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

	@Test
	void seatsUsedAreTheAssignedUsersAndUtilisationIsUsedOverPaid() {
		assertThat(LicenceRules.utilisationPercent(20, 14)).isEqualByComparingTo("70.0");
		assertThat(LicenceRules.utilisationPercent(3, 1)).isEqualByComparingTo("33.3");
		assertThat(LicenceRules.utilisationPercent(0, 0)).isNull();
		assertThat(LicenceRules.utilisationPercent(null, 4)).isNull();
		assertThat(LicenceRules.seatsFree(20, 14)).isEqualTo(6);
		assertThat(LicenceRules.seatsFree(5, 7)).isZero();
		assertThat(LicenceRules.overAssigned(5, 7)).isTrue();
		assertThat(LicenceRules.overAssigned(null, 7)).isFalse();
	}

	@Test
	void belowSeventyPercentIsUnderUsedUnlessCancelled() {
		assertThat(LicenceRules.underUsed(20, 13, LicenceStatus.ACTIVE)).isTrue();
		assertThat(LicenceRules.underUsed(20, 14, LicenceStatus.ACTIVE)).isFalse();
		assertThat(LicenceRules.underUsed(20, 0, LicenceStatus.TRIAL)).isTrue();
		assertThat(LicenceRules.underUsed(20, 0, LicenceStatus.CANCELLED)).isFalse();
		assertThat(LicenceRules.underUsed(null, 0, LicenceStatus.ACTIVE)).isFalse();
	}

	@Test
	void theAnnualCostIsTwelveMonthsOrOneYearAndNoneForOneOff() {
		assertThat(LicenceRules.annualCost(new BigDecimal("150.50"), BillingCycle.MONTHLY)).isEqualByComparingTo("1806.00");
		assertThat(LicenceRules.annualCost(new BigDecimal("9600"), BillingCycle.YEARLY)).isEqualByComparingTo("9600.00");
		assertThat(LicenceRules.annualCost(new BigDecimal("499"), BillingCycle.ONE_OFF)).isNull();
		assertThat(LicenceRules.annualCost(null, BillingCycle.YEARLY)).isNull();
	}

	@Test
	void daysToRenewalOnlyWhileSomethingIsDue() {
		assertThat(LicenceRules.daysUntilRenewal(TODAY.plusDays(20), LicenceStatus.ACTIVE, TODAY)).isEqualTo(20);
		assertThat(LicenceRules.daysUntilRenewal(TODAY, LicenceStatus.TRIAL, TODAY)).isZero();
		assertThat(LicenceRules.daysUntilRenewal(TODAY.minusDays(1), LicenceStatus.ACTIVE, TODAY)).isNull();
		assertThat(LicenceRules.daysUntilRenewal(TODAY.plusDays(5), LicenceStatus.CANCELLED, TODAY)).isNull();
		assertThat(LicenceRules.daysUntilRenewal(null, LicenceStatus.ACTIVE, TODAY)).isNull();
	}

	@Test
	void theReminderDueIsTheShortestLeadTimeTheRenewalIsWithin() {
		assertThat(LicenceRules.leadFor(30)).isEqualTo(30);
		assertThat(LicenceRules.leadFor(8)).isEqualTo(30);
		assertThat(LicenceRules.leadFor(7)).isEqualTo(7);
		assertThat(LicenceRules.leadFor(0)).isEqualTo(7);
	}

	@Test
	void paymentMethodsAreLabelsWithAtMostTheLastFourDigits() {
		assertThatCode(() -> LicenceRules.checkPaymentMethod("Corporate Amex ending 1234", "1234")).doesNotThrowAnyException();
		assertThatCode(() -> LicenceRules.checkPaymentMethod("Bank transfer", null)).doesNotThrowAnyException();
		for (String cardNumber : new String[] { "Visa 4111111111111111", "4111 1111 1111 1111", "Amex 3782-822463-10005",
				"Card 12345" }) {
			assertThatThrownBy(() -> LicenceRules.checkPaymentMethod(cardNumber, null))
				.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("CARD_NUMBER_NOT_ALLOWED"));
		}
		for (String last4 : new String[] { "123", "12345", "12a4" }) {
			assertThatThrownBy(() -> LicenceRules.checkPaymentMethod("Amex", last4))
				.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CARD_LAST4"));
		}
	}

	@Test
	void theRenewalMayNotComeBeforeTheStart() {
		assertThatCode(() -> LicenceRules.checkDates(TODAY, TODAY)).doesNotThrowAnyException();
		assertThatThrownBy(() -> LicenceRules.checkDates(TODAY, TODAY.minusDays(1))).isInstanceOf(ApiException.class);
	}

}
