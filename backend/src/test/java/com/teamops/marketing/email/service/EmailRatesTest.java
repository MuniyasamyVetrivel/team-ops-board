package com.teamops.marketing.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Email campaign rates and count checks (brief sections 38 and 80). */
class EmailRatesTest {

	/** Brief section 77: SAP Testing Services Outreach. */
	private static final EmailCounts SAMPLE = new EmailCounts(25_000, 24_000, 1_000, 9_400, 8_500, 1_400, 1_250, 60, 185);

	@Test
	void referenceRates() {
		EmailRates rates = EmailRates.of(SAMPLE);
		// Open rate = unique opens ÷ delivered × 100: 8,500 ÷ 24,000 = 35.42%.
		assertThat(rates.openRate()).isEqualByComparingTo("35.42");
		assertThat(rates.clickRate()).isEqualByComparingTo("5.21");
		assertThat(rates.leadConversionRate()).isEqualByComparingTo("0.77");
		assertThat(rates.deliveryRate()).isEqualByComparingTo("96.00");
		assertThat(rates.clickToOpenRate()).isEqualByComparingTo("14.71");
		assertThat(rates.bounceRate()).isEqualByComparingTo("4.00");
		assertThat(rates.unsubscribeRate()).isEqualByComparingTo("0.25");
	}

	@Test
	void divisionByZeroGivesNoRate() {
		EmailRates rates = EmailRates.of(EmailCounts.ZERO);
		assertThat(rates.openRate()).isNull();
		assertThat(rates.deliveryRate()).isNull();
		assertThat(rates.clickToOpenRate()).isNull();
		// Delivered but nobody opened: a real 0%, and click-to-open has no denominator.
		EmailRates unopened = EmailRates.of(new EmailCounts(100, 100, 0, 0, 0, 0, 0, 0, 0));
		assertThat(unopened.openRate()).isEqualByComparingTo("0");
		assertThat(unopened.clickToOpenRate()).isNull();
	}

	@Test
	void monthlyRatesComeFromSummedCounts() {
		// 8,500 + 100 opens over 24,000 + 1,000 delivered = 34.40%, not the 22.71% average of 35.42% and 10%.
		EmailCounts small = new EmailCounts(1_000, 1_000, 0, 100, 100, 10, 10, 0, 1);
		assertThat(EmailRates.of(SAMPLE.plus(small)).openRate()).isEqualByComparingTo("34.40");
		assertThat(SAMPLE.plus(small).leads()).isEqualTo(186);
	}

	@Test
	void inconsistentCountsAreExplainedFieldByField() {
		assertThat(SAMPLE.problems()).isEmpty();
		EmailCounts wrong = new EmailCounts(100, 120, 200, 10, 20, 5, 30, 200, 0);
		assertThat(wrong.problems()).extracting(EmailCounts.Problem::field)
			.containsExactly("delivered", "bounced", "uniqueOpens", "uniqueClicks", "unsubscribed");
		assertThat(EmailCounts.ZERO.isZero()).isTrue();
		assertThat(SAMPLE.isZero()).isFalse();
	}

	@Test
	void fieldNamesReadAsWords() {
		assertThat(EmailCampaignService.label("uniqueOpens")).isEqualTo("Unique opens");
		assertThat(EmailCampaignService.label("delivered")).isEqualTo("Delivered");
	}

}
