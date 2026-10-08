package com.teamops.marketing.common;

import java.math.BigDecimal;

/**
 * Progress of a monthly target (brief section 30). Achievement % = actual ÷ target × 100, remaining = max(target −
 * actual, 0). Status: actual ≥ target is ACHIEVED; achievement below the threshold is BEHIND; anything else is
 * IN_PROGRESS. A missing actual counts as zero.
 */
public record TargetProgress(BigDecimal target, BigDecimal actual, BigDecimal achievementPct, BigDecimal remaining,
		TargetStatus status) {

	public enum TargetStatus {

		/** Green. */
		ACHIEVED,
		/** Orange. */
		IN_PROGRESS,
		/** Red. */
		BEHIND

	}

	public static TargetProgress of(BigDecimal target, BigDecimal actual, BigDecimal behindThresholdPct) {
		BigDecimal achieved = actual == null ? BigDecimal.ZERO : actual;
		BigDecimal pct = MarketingMath.percent(achieved, target);
		TargetStatus status;
		if (achieved.compareTo(target) >= 0) {
			status = TargetStatus.ACHIEVED;
		}
		else if (pct != null && pct.compareTo(behindThresholdPct) < 0) {
			status = TargetStatus.BEHIND;
		}
		else {
			status = TargetStatus.IN_PROGRESS;
		}
		return new TargetProgress(target, achieved, pct, MarketingMath.remaining(target, achieved), status);
	}

}
