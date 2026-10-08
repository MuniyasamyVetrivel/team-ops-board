package com.teamops.marketing.paid.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.teamops.marketing.common.MarketingMonths;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.paid.entity.PaidCampaign;

/** Paid campaign rules from the brief (sections 41, 42, 76 and 80). */
class PaidRatesTest {

	/** Brief section 76: ₹42,000 spent, 150,000 impressions, 2,800 clicks, 84 leads. */
	private static final PaidResults SAP = new PaidResults(new BigDecimal("42000"), 150_000, 2_800, 84, 21);

	@Test
	void ratesFollowTheBrief() {
		PaidRates rates = PaidRates.of(SAP);
		assertThat(rates.costPerLead()).isEqualByComparingTo("500.00");
		assertThat(rates.ctr()).isEqualByComparingTo("1.87");
		assertThat(rates.conversionRate()).isEqualByComparingTo("25.00");
		assertThat(rates.costPerClick()).isEqualByComparingTo("15.00");
	}

	@Test
	void zeroDenominatorsGiveNull() {
		PaidRates rates = PaidRates.of(PaidResults.ZERO);
		assertThat(rates.ctr()).isNull();
		assertThat(rates.costPerLead()).isNull();
		assertThat(rates.conversionRate()).isNull();
		assertThat(rates.costPerClick()).isNull();
	}

	@Test
	void budgetProgressShowsRemainingAndOverspend() {
		BudgetProgress progress = BudgetProgress.of(new BigDecimal("50000.00"), new BigDecimal("42000.00"));
		assertThat(progress.remaining()).isEqualByComparingTo("8000.00");
		assertThat(progress.usedPct()).isEqualByComparingTo("84.00");
		assertThat(progress.overBudget()).isFalse();

		BudgetProgress over = BudgetProgress.of(new BigDecimal("50000.00"), new BigDecimal("53000.00"));
		assertThat(over.remaining()).isEqualByComparingTo("-3000.00");
		assertThat(over.overBudget()).isTrue();
	}

	@Test
	void resultsMustBeConsistentAndSumAcrossMonths() {
		assertThat(SAP.problems()).isEmpty();
		assertThat(new PaidResults(BigDecimal.TEN, 100, 120, 5, 6).problems()).extracting(PaidResults.Problem::field)
			.containsExactly("clicks", "conversions");

		PaidResults lifetime = SAP.plus(new PaidResults(new BigDecimal("8000"), 50_000, 1_200, 16, 4));
		assertThat(lifetime.spend()).isEqualByComparingTo("50000.00");
		assertThat(lifetime.leads()).isEqualTo(100);
		assertThat(PaidRates.of(lifetime).costPerLead()).isEqualByComparingTo("500.00");
		assertThat(PaidRates.of(lifetime).ctr()).isEqualByComparingTo("2.00");
	}

	@Test
	void campaignsRunInTheMonthsTheirDatesOverlap() {
		PaidCampaign campaign = new PaidCampaign();
		campaign.setStartDate(LocalDate.of(2026, 9, 20));
		campaign.setEndDate(LocalDate.of(2026, 11, 3));
		assertThat(campaign.runsIn(new MarketingPeriod(8, 2026))).isFalse();
		assertThat(campaign.runsIn(new MarketingPeriod(9, 2026))).isTrue();
		assertThat(campaign.runsIn(new MarketingPeriod(11, 2026))).isTrue();
		assertThat(campaign.runsIn(new MarketingPeriod(12, 2026))).isFalse();

		campaign.setEndDate(null);
		assertThat(campaign.runsIn(new MarketingPeriod(5, 2027))).isTrue();
	}

	@Test
	void onlyOpenMonthsAreCorrectableExceptBySuperAdmin() {
		LocalDate today = LocalDate.of(2026, 10, 9);
		MarketingPeriod october = new MarketingPeriod(10, 2026);
		MarketingPeriod september = new MarketingPeriod(9, 2026);
		MarketingPeriod august = new MarketingPeriod(8, 2026);
		MarketingPeriod november = new MarketingPeriod(11, 2026);

		assertThat(MarketingMonths.canCorrect(october, today, false)).isTrue();
		assertThat(MarketingMonths.canCorrect(september, today, false)).isTrue();
		assertThat(MarketingMonths.canCorrect(august, today, false)).isFalse();
		assertThat(MarketingMonths.canCorrect(august, today, true)).isTrue();
		assertThat(MarketingMonths.canCorrect(november, today, true)).isFalse();
		assertThat(MarketingMonths.isFuture(november, today)).isTrue();
	}

}
