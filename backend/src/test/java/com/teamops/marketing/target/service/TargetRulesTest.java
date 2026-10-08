package com.teamops.marketing.target.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.TargetProgress;
import com.teamops.marketing.common.TargetProgress.TargetStatus;
import com.teamops.marketing.target.dto.TargetDtos.TrendView;
import com.teamops.marketing.target.entity.TargetUnit;
import com.teamops.marketing.target.service.TargetRules.ActualOrigin;

/** Monthly target rules (brief sections 30–33). */
class TargetRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

	private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("60");

	@Test
	void referenceValues() {
		// 250 target, 200 actual → 80% with 50 remaining (above the 60% threshold, so still in progress).
		TargetProgress partial = TargetProgress.of(new BigDecimal("250"), new BigDecimal("200"), DEFAULT_THRESHOLD);
		assertThat(partial.achievementPct()).isEqualByComparingTo("80");
		assertThat(partial.remaining()).isEqualByComparingTo("50");
		assertThat(partial.status()).isEqualTo(TargetStatus.IN_PROGRESS);

		// 250 target, 275 actual → 110% with 0 remaining, achieved.
		TargetProgress exceeded = TargetProgress.of(new BigDecimal("250"), new BigDecimal("275"), DEFAULT_THRESHOLD);
		assertThat(exceeded.achievementPct()).isEqualByComparingTo("110");
		assertThat(exceeded.remaining()).isEqualByComparingTo("0");
		assertThat(exceeded.status()).isEqualTo(TargetStatus.ACHIEVED);
	}

	@Test
	void theBehindThresholdIsConfigurablePerType() {
		assertThat(TargetProgress.of(new BigDecimal("250"), new BigDecimal("100"), DEFAULT_THRESHOLD).status())
			.isEqualTo(TargetStatus.BEHIND);
		// A type that expects 85% is behind at 80%.
		assertThat(TargetProgress.of(new BigDecimal("250"), new BigDecimal("200"), new BigDecimal("85")).status())
			.isEqualTo(TargetStatus.BEHIND);
		// Exactly at the threshold is not behind.
		assertThat(TargetProgress.of(new BigDecimal("250"), new BigDecimal("150"), DEFAULT_THRESHOLD).status())
			.isEqualTo(TargetStatus.IN_PROGRESS);
		// No actual yet counts as zero.
		assertThat(TargetProgress.of(new BigDecimal("250"), null, DEFAULT_THRESHOLD).remaining())
			.isEqualByComparingTo("250");
	}

	@Test
	void monthsFromThePreviousOneOnwardAreOpen() {
		assertThat(TargetRules.isOpen(new MarketingPeriod(9, 2026), TODAY)).isTrue();
		assertThat(TargetRules.isOpen(new MarketingPeriod(10, 2026), TODAY)).isTrue();
		assertThat(TargetRules.isOpen(new MarketingPeriod(3, 2027), TODAY)).isTrue();
		assertThat(TargetRules.isOpen(new MarketingPeriod(8, 2026), TODAY)).isFalse();
		assertThat(TargetRules.canChange(new MarketingPeriod(1, 2025), TODAY, false)).isFalse();
		assertThat(TargetRules.canChange(new MarketingPeriod(1, 2025), TODAY, true)).isTrue();
		assertThat(TargetRules.isFuture(new MarketingPeriod(11, 2026), TODAY)).isTrue();
		assertThat(TargetRules.isFuture(new MarketingPeriod(10, 2026), TODAY)).isFalse();
	}

	@Test
	void valuesMatchTheUnit() {
		assertThat(TargetRules.requireValid(TargetUnit.COUNT, new BigDecimal("250.00"), "target")).isEqualByComparingTo("250");
		assertThatThrownBy(() -> TargetRules.requireValid(TargetUnit.COUNT, new BigDecimal("10.5"), "target"))
			.isInstanceOf(ApiException.class)
			.extracting("code")
			.isEqualTo("INVALID_VALUE");
		assertThat(TargetRules.requireValid(TargetUnit.CURRENCY, new BigDecimal("42000.50"), "target")).isNotNull();
		assertThatThrownBy(() -> TargetRules.requireValid(TargetUnit.PERCENT, new BigDecimal("100.01"), "actual"))
			.isInstanceOf(ApiException.class);
		assertThat(TargetRules.requireValid(TargetUnit.PERCENT, null, "actual")).isNull();
	}

	@Test
	void aStoredActualAlwaysWins() {
		assertThat(TargetRules.resolve(new BigDecimal("31"), false, null).origin()).isEqualTo(ActualOrigin.MANUAL);
		// Entered by hand before the source became automatic: kept, so history does not change.
		assertThat(TargetRules.resolve(new BigDecimal("22"), true, new BigDecimal("25")).value()).isEqualByComparingTo("22");
		assertThat(TargetRules.resolve(null, true, new BigDecimal("5")).origin()).isEqualTo(ActualOrigin.AUTOMATIC);
		assertThat(TargetRules.resolve(null, true, null).origin()).isEqualTo(ActualOrigin.NONE);
		assertThat(TargetRules.resolve(null, false, new BigDecimal("5")).value()).isNull();
	}

	@Test
	void quartersAndYearsAddCountsAndAveragePercentages() {
		List<BigDecimal> months = Arrays.asList(new BigDecimal("200"), null, new BigDecimal("250"));
		assertThat(TargetRules.combine(TargetUnit.COUNT, months)).isEqualByComparingTo("450");
		assertThat(TargetRules.combine(TargetUnit.CURRENCY, months)).isEqualByComparingTo("450");
		assertThat(TargetRules.combine(TargetUnit.PERCENT, List.of(new BigDecimal("30"), new BigDecimal("35.5"))))
			.isEqualByComparingTo("32.75");
		assertThat(TargetRules.combine(TargetUnit.COUNT, Arrays.asList(null, null))).isNull();
	}

	@Test
	void trendBucketsCoverTheYear() {
		assertThat(TargetService.buckets(TrendView.MONTH, 2026)).hasSize(12);
		assertThat(TargetService.buckets(TrendView.QUARTER, 2026)).hasSize(4)
			.element(3)
			.isEqualTo(List.of(new MarketingPeriod(10, 2026), new MarketingPeriod(11, 2026), new MarketingPeriod(12, 2026)));
		List<List<MarketingPeriod>> years = TargetService.buckets(TrendView.YEAR, 2026);
		assertThat(years).hasSize(5);
		assertThat(years.getFirst().getFirst()).isEqualTo(new MarketingPeriod(1, 2022));
	}

	@Test
	void codesAreDerivedFromNames() {
		assertThat(TargetTypeService.codeFrom("Webinar Sign-ups")).isEqualTo("WEBINAR_SIGN_UPS");
		assertThat(TargetTypeService.codeFrom("2026 Events")).isEqualTo("TYPE_2026_EVENTS");
	}

}
