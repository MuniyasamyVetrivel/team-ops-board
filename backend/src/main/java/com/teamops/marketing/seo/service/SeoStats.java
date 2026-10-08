package com.teamops.marketing.seo.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.common.RankingChange.Movement;

/**
 * SEO figures for a set of keywords in one month (brief section 28), computed from their standings, never stored.
 *
 * @param top3 positions 1–3 (also counted in {@code top10})
 * @param top10 positions 1–10 (TOP 10, green)
 * @param ranking positions 11–100 (RANKING, orange)
 * @param notRanked no position: recorded as Not Ranked, or nothing recorded for the month (red)
 * @param notRecorded keywords without a row for the month (included in {@code notRanked})
 * @param averagePosition mean of the ranked positions to two decimals, {@code null} when none ranked ("—")
 */
public record SeoStats(int totalKeywords, int top3, int top10, int ranking, int notRanked, int notRecorded,
		int improved, int declined, int unchanged, BigDecimal averagePosition) {

	public static final SeoStats EMPTY = of(List.of());

	private static final int TOP_3 = 3;

	public static SeoStats of(Collection<KeywordStanding> standings) {
		int top3 = 0;
		int top10 = 0;
		int ranking = 0;
		int notRanked = 0;
		int notRecorded = 0;
		int improved = 0;
		int declined = 0;
		int unchanged = 0;
		long positionSum = 0;
		for (KeywordStanding standing : standings) {
			switch (standing.status()) {
				case TOP_10 -> {
					top10++;
					if (standing.position() <= TOP_3) {
						top3++;
					}
				}
				case RANKING -> ranking++;
				case NOT_RANKED -> notRanked++;
			}
			if (standing.position() != null) {
				positionSum += standing.position();
			}
			if (!standing.recorded()) {
				notRecorded++;
			}
			if (standing.change() != null) {
				Movement movement = standing.change().movement();
				if (movement == Movement.IMPROVED) {
					improved++;
				}
				else if (movement == Movement.DECLINED) {
					declined++;
				}
				else if (movement == Movement.UNCHANGED) {
					unchanged++;
				}
			}
		}
		return new SeoStats(standings.size(), top3, top10, ranking, notRanked, notRecorded, improved, declined,
				unchanged, MarketingMath.perUnit(positionSum, top10 + ranking));
	}

}
