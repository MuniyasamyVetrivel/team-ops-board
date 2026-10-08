package com.teamops.marketing.seo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.RankingStatus;

/** Monthly ranking rules (brief sections 25, 26 and 29). */
class RankingRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

	@Test
	void referencePositionsGetTheirColour() {
		assertThat(RankingStatus.of(7)).isEqualTo(RankingStatus.TOP_10);
		assertThat(RankingStatus.of(15)).isEqualTo(RankingStatus.RANKING);
		assertThat(RankingStatus.of(null)).isEqualTo(RankingStatus.NOT_RANKED);
		assertThat(RankingStatus.of(10)).isEqualTo(RankingStatus.TOP_10);
		assertThat(RankingStatus.of(100)).isEqualTo(RankingStatus.RANKING);
	}

	@Test
	void onlyPastAndCurrentMonthsCanBeRecorded() {
		assertThat(RankingRules.isRecordable(new MarketingPeriod(10, 2026), TODAY)).isTrue();
		assertThat(RankingRules.isRecordable(new MarketingPeriod(1, 2020), TODAY)).isTrue();
		assertThat(RankingRules.isRecordable(new MarketingPeriod(11, 2026), TODAY)).isFalse();
		assertThatThrownBy(() -> RankingRules.requireRecordable(new MarketingPeriod(1, 2027), TODAY))
			.isInstanceOf(ApiException.class)
			.extracting("code")
			.isEqualTo("FUTURE_PERIOD");
	}

	@Test
	void onlyTheCurrentAndPreviousMonthCanBeCorrected() {
		assertThat(RankingRules.isCorrectable(new MarketingPeriod(10, 2026), TODAY)).isTrue();
		assertThat(RankingRules.isCorrectable(new MarketingPeriod(9, 2026), TODAY)).isTrue();
		assertThat(RankingRules.isCorrectable(new MarketingPeriod(8, 2026), TODAY)).isFalse();
		assertThat(RankingRules.isCorrectable(new MarketingPeriod(10, 2025), TODAY)).isFalse();
		// Across a year boundary: in January, December is still open.
		LocalDate january = LocalDate.of(2027, 1, 3);
		assertThat(RankingRules.isCorrectable(new MarketingPeriod(12, 2026), january)).isTrue();
		assertThat(RankingRules.isCorrectable(new MarketingPeriod(11, 2026), january)).isFalse();
	}

	@Test
	void superAdminsMayCorrectAnyPastMonth() {
		MarketingPeriod closed = new MarketingPeriod(3, 2024);
		assertThat(RankingRules.canCorrect(closed, TODAY, false)).isFalse();
		assertThat(RankingRules.canCorrect(closed, TODAY, true)).isTrue();
		assertThat(RankingRules.canCorrect(new MarketingPeriod(9, 2026), TODAY, false)).isTrue();
		// Still never the future.
		assertThat(RankingRules.canCorrect(new MarketingPeriod(11, 2026), TODAY, true)).isFalse();
	}

	@Test
	void positionCellsAcceptNumbersAndNrButNeverBlank() {
		assertThat(RankingRules.parsePosition("7").position()).isEqualTo(7);
		assertThat(RankingRules.parsePosition(" #12 ").position()).isEqualTo(12);
		assertThat(RankingRules.parsePosition("NR").position()).isNull();
		assertThat(RankingRules.parsePosition("not ranked").position()).isNull();
		assertThat(RankingRules.parsePosition("n/a")).isNotNull();
		assertThat(RankingRules.parsePosition("")).isNull();
		assertThat(RankingRules.parsePosition(null)).isNull();
		assertThat(RankingRules.parsePosition("0")).isNull();
		assertThat(RankingRules.parsePosition("101")).isNull();
		assertThat(RankingRules.parsePosition("seven")).isNull();
	}

	@Test
	void positionsOutsideOneToHundredAreRejected() {
		assertThat(RankingRules.requireValidPosition(null)).isNull();
		assertThat(RankingRules.requireValidPosition(100)).isEqualTo(100);
		assertThatThrownBy(() -> RankingRules.requireValidPosition(0)).isInstanceOf(ApiException.class)
			.extracting("code")
			.isEqualTo("INVALID_POSITION");
	}

	@Test
	void monthlyReportBandsCoverEveryPosition() {
		SeoStats stats = SeoStats.of(List.of(standing(1), standing(3), standing(7), standing(10), standing(11),
				standing(20), standing(21), standing(50), standing(51), standing(100), standing(null),
				KeywordStanding.unrecorded()));

		assertThat(stats.totalKeywords()).isEqualTo(12);
		assertThat(stats.top3()).isEqualTo(2);
		assertThat(stats.top10()).isEqualTo(4);
		assertThat(stats.positions11to20()).isEqualTo(2);
		assertThat(stats.positions21to50()).isEqualTo(2);
		assertThat(stats.positions51to100()).isEqualTo(2);
		assertThat(stats.ranking()).isEqualTo(6);
		assertThat(stats.notRanked()).isEqualTo(2);
		assertThat(stats.notRecorded()).isEqualTo(1);
		assertThat(stats.averagePosition()).isEqualByComparingTo(new BigDecimal("27.40"));
	}

	@Test
	void periodsStepAcrossYears() {
		assertThat(new MarketingPeriod(12, 2026).next()).isEqualTo(new MarketingPeriod(1, 2027));
		assertThat(new MarketingPeriod(2, 2026).plusMonths(-3)).isEqualTo(new MarketingPeriod(11, 2025));
	}

	private static KeywordStanding standing(Integer position) {
		return KeywordStanding.of(true, position, false, null);
	}

}
