package com.teamops.marketing.seo.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.common.RankingChange.Movement;

/**
 * SEO figures for a set of keywords in one month (brief sections 28 and 29), computed from their standings, never
 * stored.
 *
 * @param top3 positions 1–3 (also counted in {@code top10})
 * @param top10 positions 1–10 (TOP 10, green)
 * @param ranking positions 11–100 (RANKING, orange), split into the three bands below
 * @param positions11to20 positions 11–20
 * @param positions21to50 positions 21–50
 * @param positions51to100 positions 51–100
 * @param notRanked no position: recorded as Not Ranked, or nothing recorded for the month (red)
 * @param notRecorded keywords without a row for the month (included in {@code notRanked})
 * @param averagePosition mean of the ranked positions to two decimals, {@code null} when none ranked ("—")
 */
public record SeoStats(int totalKeywords, int top3, int top10, int ranking, int positions11to20, int positions21to50,
		int positions51to100, int notRanked, int notRecorded, int improved, int declined, int unchanged,
		BigDecimal averagePosition) {

	public static final SeoStats EMPTY = of(List.of());

	private static final int TOP_3 = 3;

	private static final int BAND_20 = 20;

	private static final int BAND_50 = 50;

	public static SeoStats of(Collection<KeywordStanding> standings) {
		int top3 = 0;
		int top10 = 0;
		int to20 = 0;
		int to50 = 0;
		int to100 = 0;
		int notRanked = 0;
		int notRecorded = 0;
		int improved = 0;
		int declined = 0;
		int unchanged = 0;
		long positionSum = 0;
		for (KeywordStanding standing : standings) {
			Integer position = standing.position();
			if (position == null) {
				notRanked++;
			}
			else {
				positionSum += position;
				if (position <= TOP_3) {
					top3++;
				}
				if (position <= 10) {
					top10++;
				}
				else if (position <= BAND_20) {
					to20++;
				}
				else if (position <= BAND_50) {
					to50++;
				}
				else {
					to100++;
				}
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
		int ranking = to20 + to50 + to100;
		return new SeoStats(standings.size(), top3, top10, ranking, to20, to50, to100, notRanked, notRecorded,
				improved, declined, unchanged, MarketingMath.perUnit(positionSum, top10 + ranking));
	}

}
