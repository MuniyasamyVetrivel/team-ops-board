package com.teamops.marketing.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.common.RankingChange.Movement;
import com.teamops.marketing.common.TargetProgress.TargetStatus;

/** The marketing business rules from CLAUDE.md, with the brief's reference values. */
class MarketingRulesTest {

	private static final BigDecimal THRESHOLD = new BigDecimal("60");

	@Test
	void rankingStatusFollowsThePositionBuckets() {
		assertThat(RankingStatus.of(7)).isEqualTo(RankingStatus.TOP_10);
		assertThat(RankingStatus.of(1)).isEqualTo(RankingStatus.TOP_10);
		assertThat(RankingStatus.of(10)).isEqualTo(RankingStatus.TOP_10);
		assertThat(RankingStatus.of(11)).isEqualTo(RankingStatus.RANKING);
		assertThat(RankingStatus.of(15)).isEqualTo(RankingStatus.RANKING);
		assertThat(RankingStatus.of(100)).isEqualTo(RankingStatus.RANKING);
		assertThat(RankingStatus.of(null)).isEqualTo(RankingStatus.NOT_RANKED);
		assertThat(RankingStatus.TOP_10.tone()).isEqualTo("GREEN");
		assertThat(RankingStatus.RANKING.tone()).isEqualTo("ORANGE");
		assertThat(RankingStatus.NOT_RANKED.label()).isEqualTo("NOT RANKED");
		assertThat(RankingStatus.NOT_RANKED.tone()).isEqualTo("RED");
		assertThatThrownBy(() -> RankingStatus.of(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RankingStatus.of(101)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rankingChangeIsPreviousMinusCurrent() {
		assertThat(RankingChange.of(true, 12, 7)).isEqualTo(new RankingChange(5, Movement.IMPROVED));
		assertThat(RankingChange.of(true, 7, 12)).isEqualTo(new RankingChange(-5, Movement.DECLINED));
		assertThat(RankingChange.of(true, 9, 9)).isEqualTo(new RankingChange(0, Movement.UNCHANGED));
	}

	@Test
	void enteringOrLeavingTheRankingsHasADirectionButNoNumber() {
		assertThat(RankingChange.of(true, null, 40)).isEqualTo(new RankingChange(null, Movement.IMPROVED));
		assertThat(RankingChange.of(true, 40, null)).isEqualTo(new RankingChange(null, Movement.DECLINED));
		assertThat(RankingChange.of(true, null, null)).isEqualTo(new RankingChange(null, Movement.UNCHANGED));
		assertThat(RankingChange.of(false, null, 4)).isEqualTo(new RankingChange(null, Movement.NEW));
	}

	@Test
	void targetBelowTargetIsInProgressWithRemaining() {
		TargetProgress progress = TargetProgress.of(new BigDecimal("250"), new BigDecimal("200"), THRESHOLD);
		assertThat(progress.achievementPct()).isEqualByComparingTo("80");
		assertThat(progress.remaining()).isEqualByComparingTo("50");
		assertThat(progress.status()).isEqualTo(TargetStatus.IN_PROGRESS);
	}

	@Test
	void targetExceededIsAchievedWithNothingRemaining() {
		TargetProgress progress = TargetProgress.of(new BigDecimal("250"), new BigDecimal("275"), THRESHOLD);
		assertThat(progress.achievementPct()).isEqualByComparingTo("110");
		assertThat(progress.remaining()).isEqualByComparingTo("0");
		assertThat(progress.status()).isEqualTo(TargetStatus.ACHIEVED);
	}

	@Test
	void targetBelowTheThresholdIsBehind() {
		assertThat(TargetProgress.of(new BigDecimal("100"), new BigDecimal("59"), THRESHOLD).status())
			.isEqualTo(TargetStatus.BEHIND);
		assertThat(TargetProgress.of(new BigDecimal("100"), new BigDecimal("60"), THRESHOLD).status())
			.isEqualTo(TargetStatus.IN_PROGRESS);
		assertThat(TargetProgress.of(new BigDecimal("100"), new BigDecimal("100"), THRESHOLD).status())
			.isEqualTo(TargetStatus.ACHIEVED);
		// A per-type override changes the line.
		assertThat(TargetProgress.of(new BigDecimal("100"), new BigDecimal("70"), new BigDecimal("75")).status())
			.isEqualTo(TargetStatus.BEHIND);
		// No actual yet counts as zero.
		TargetProgress empty = TargetProgress.of(new BigDecimal("12"), null, THRESHOLD);
		assertThat(empty.actual()).isEqualByComparingTo("0");
		assertThat(empty.remaining()).isEqualByComparingTo("12");
		assertThat(empty.status()).isEqualTo(TargetStatus.BEHIND);
	}

	@Test
	void aZeroTargetHasNoAchievementPercentage() {
		TargetProgress progress = TargetProgress.of(BigDecimal.ZERO, BigDecimal.ZERO, THRESHOLD);
		assertThat(progress.achievementPct()).isNull();
		assertThat(progress.status()).isEqualTo(TargetStatus.ACHIEVED);
	}

	@Test
	void emailRates() {
		assertThat(MarketingMath.openRate(8_500, 24_000)).isEqualByComparingTo("35.42");
		assertThat(MarketingMath.clickRate(1_250, 24_000)).isEqualByComparingTo("5.21");
		assertThat(MarketingMath.leadConversion(185, 24_000)).isEqualByComparingTo("0.77");
		assertThat(MarketingMath.openRate(10, 0)).isNull();
	}

	@Test
	void paidCampaignRates() {
		assertThat(MarketingMath.costPerLead(new BigDecimal("42000"), 84)).isEqualByComparingTo("500.00");
		assertThat(MarketingMath.clickThroughRate(1_200, 80_000)).isEqualByComparingTo("1.50");
		assertThat(MarketingMath.conversionRate(21, 84)).isEqualByComparingTo("25.00");
		assertThat(MarketingMath.remainingBudget(new BigDecimal("50000"), new BigDecimal("42000")))
			.isEqualByComparingTo("8000");
		assertThat(MarketingMath.remainingBudget(new BigDecimal("50000"), new BigDecimal("51000")))
			.isEqualByComparingTo("-1000");
		assertThat(MarketingMath.costPerLead(new BigDecimal("42000"), 0)).isNull();
		assertThat(MarketingMath.clickThroughRate(5, null)).isNull();
	}

	@Test
	void periodDefaultsToTodayAndKnowsItsNeighbours() {
		LocalDate today = LocalDate.of(2026, 1, 15);
		MarketingPeriod period = MarketingPeriod.resolve(null, null, today);
		assertThat(period).isEqualTo(new MarketingPeriod(1, 2026));
		assertThat(period.previous()).isEqualTo(new MarketingPeriod(12, 2025));
		assertThat(period.label()).isEqualTo("January 2026");
		assertThat(new MarketingPeriod(2, 2028).lastDay()).isEqualTo(LocalDate.of(2028, 2, 29));
		assertThat(new MarketingPeriod(10, 2026).quarter()).isEqualTo(4);
		assertThat(MarketingPeriod.resolve(10, null, today)).isEqualTo(new MarketingPeriod(10, 2026));
		assertThatThrownBy(() -> new MarketingPeriod(13, 2026)).isInstanceOf(ApiException.class)
			.hasMessageContaining("Month");
		assertThatThrownBy(() -> new MarketingPeriod(1, 1999)).isInstanceOf(ApiException.class);
	}

}
