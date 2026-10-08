package com.teamops.marketing.common;

/**
 * Month-over-month ranking movement. Change = previous − current, so a positive value means the keyword climbed.
 * Entering or leaving the rankings has no numeric change but still counts as improved or declined.
 *
 * @param value the numeric change, or {@code null} when either month is not ranked or there is no previous month
 */
public record RankingChange(Integer value, Movement movement) {

	public enum Movement {

		/** Climbed (green up arrow), including Not ranked → ranked. */
		IMPROVED,
		/** Dropped (red down arrow), including ranked → Not ranked. */
		DECLINED,
		/** Same position, or Not ranked both months (gray). */
		UNCHANGED,
		/** No ranking was recorded for the previous month. */
		NEW

	}

	/**
	 * @param previousRecorded whether a ranking row exists for the previous month (a recorded NR is still recorded)
	 * @param previous previous month's position, {@code null} for Not ranked
	 * @param current this month's position, {@code null} for Not ranked
	 */
	public static RankingChange of(boolean previousRecorded, Integer previous, Integer current) {
		if (!previousRecorded) {
			return new RankingChange(null, Movement.NEW);
		}
		if (previous == null && current == null) {
			return new RankingChange(null, Movement.UNCHANGED);
		}
		if (previous == null) {
			return new RankingChange(null, Movement.IMPROVED);
		}
		if (current == null) {
			return new RankingChange(null, Movement.DECLINED);
		}
		int change = previous - current;
		Movement movement = change > 0 ? Movement.IMPROVED : change < 0 ? Movement.DECLINED : Movement.UNCHANGED;
		return new RankingChange(change, movement);
	}

}
