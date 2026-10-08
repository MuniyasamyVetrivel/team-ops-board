package com.teamops.marketing.seo.service;

import com.teamops.marketing.common.RankingChange;
import com.teamops.marketing.common.RankingStatus;

/**
 * A keyword's ranking for one month, computed from its history rows for that month and the month before.
 *
 * @param recorded whether a row exists for the month (a recorded Not Ranked is still recorded)
 * @param position the month's position, {@code null} when not ranked or not recorded
 * @param status NOT_RANKED when not ranked or not recorded ("position unavailable")
 * @param change movement since the previous month, {@code null} when the month has no row
 */
public record KeywordStanding(boolean recorded, Integer position, RankingStatus status, boolean previousRecorded,
		Integer previousPosition, RankingChange change) {

	public static KeywordStanding of(boolean recorded, Integer position, boolean previousRecorded,
			Integer previousPosition) {
		Integer current = recorded ? position : null;
		Integer previous = previousRecorded ? previousPosition : null;
		return new KeywordStanding(recorded, current, RankingStatus.of(current), previousRecorded, previous,
				recorded ? RankingChange.of(previousRecorded, previous, current) : null);
	}

	public static KeywordStanding unrecorded() {
		return of(false, null, false, null);
	}

}
