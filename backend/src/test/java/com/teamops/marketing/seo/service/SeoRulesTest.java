package com.teamops.marketing.seo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.marketing.common.RankingChange.Movement;
import com.teamops.marketing.common.RankingStatus;

/** Keyword standings and page statistics (brief sections 24, 26 and 28). */
class SeoRulesTest {

	@Test
	void standingUsesTheRankingColourRules() {
		assertThat(KeywordStanding.of(true, 7, true, 12).status()).isEqualTo(RankingStatus.TOP_10);
		assertThat(KeywordStanding.of(true, 15, true, 15).status()).isEqualTo(RankingStatus.RANKING);
		assertThat(KeywordStanding.of(true, null, true, 40).status()).isEqualTo(RankingStatus.NOT_RANKED);
	}

	@Test
	void changeIsPreviousMinusCurrent() {
		// Brief example: SAP S/4HANA Testing Services, previous 12, current 7 → +5 positions.
		KeywordStanding climbed = KeywordStanding.of(true, 7, true, 12);
		assertThat(climbed.change().value()).isEqualTo(5);
		assertThat(climbed.change().movement()).isEqualTo(Movement.IMPROVED);

		KeywordStanding dropped = KeywordStanding.of(true, 31, true, 28);
		assertThat(dropped.change().value()).isEqualTo(-3);
		assertThat(dropped.change().movement()).isEqualTo(Movement.DECLINED);

		assertThat(KeywordStanding.of(true, 12, true, 12).change().movement()).isEqualTo(Movement.UNCHANGED);
		assertThat(KeywordStanding.of(true, 42, false, null).change().movement()).isEqualTo(Movement.NEW);
	}

	@Test
	void aMonthWithoutARowIsNotRankedWithNoMovement() {
		KeywordStanding missing = KeywordStanding.of(false, null, true, 9);
		assertThat(missing.recorded()).isFalse();
		assertThat(missing.status()).isEqualTo(RankingStatus.NOT_RANKED);
		assertThat(missing.change()).isNull();
		assertThat(missing.previousPosition()).isEqualTo(9);

		assertThat(KeywordStanding.unrecorded().previousRecorded()).isFalse();
	}

	@Test
	void pageStatisticsCountEveryBucket() {
		SeoStats stats = SeoStats.of(List.of(KeywordStanding.of(true, 2, true, 5), // top 3, improved
				KeywordStanding.of(true, 7, true, 12), // top 10, improved
				KeywordStanding.of(true, 31, true, 28), // ranking, declined
				KeywordStanding.of(true, null, true, 19), // not ranked, declined (dropped out)
				KeywordStanding.of(true, 10, true, 10), // top 10, unchanged
				KeywordStanding.of(false, null, true, 50))); // not recorded: not ranked, no movement

		assertThat(stats.totalKeywords()).isEqualTo(6);
		assertThat(stats.top3()).isEqualTo(1);
		assertThat(stats.top10()).isEqualTo(3);
		assertThat(stats.ranking()).isEqualTo(1);
		assertThat(stats.notRanked()).isEqualTo(2);
		assertThat(stats.notRecorded()).isEqualTo(1);
		assertThat(stats.improved()).isEqualTo(2);
		assertThat(stats.declined()).isEqualTo(2);
		assertThat(stats.unchanged()).isEqualTo(1);
		// (2 + 7 + 31 + 10) ÷ 4 ranked keywords.
		assertThat(stats.averagePosition()).isEqualByComparingTo(new BigDecimal("12.50"));
	}

	@Test
	void averagePositionIsEmptyWhenNothingRanks() {
		SeoStats stats = SeoStats.of(List.of(KeywordStanding.of(true, null, false, null)));
		assertThat(stats.averagePosition()).isNull();
		assertThat(stats.notRanked()).isEqualTo(1);
		assertThat(SeoStats.EMPTY.totalKeywords()).isZero();
		assertThat(SeoStats.EMPTY.averagePosition()).isNull();
	}

	@Test
	void keywordTextIsTrimmedAndCollapsed() {
		assertThat(SeoService.normalize("  SAP   Testing\tServices ")).isEqualTo("SAP Testing Services");
		assertThat(SeoService.normalize("   ")).isNull();
		assertThat(SeoService.normalize(null)).isNull();
	}

}
