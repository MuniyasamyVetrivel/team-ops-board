package com.teamops.marketing.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.teamops.marketing.common.TargetProgress.TargetStatus;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.user.dto.UserSummary;

/**
 * The Digital Marketing monthly report (brief section 50): every module's figures for the month against the month
 * before. A group is left out when the viewer lacks its module's view permission; targets and keyword movements are
 * null without TARGET_VIEW and SEO_VIEW.
 */
public final class MarketingReportDtos {

	private MarketingReportDtos() {
	}

	public enum Unit {
		COUNT, PERCENT, CURRENCY, DECIMAL
	}

	/** Whether a rise is good news (leads), bad news (cost per lead), or neither (spend). */
	public enum Better {
		HIGHER, LOWER, NEITHER
	}

	/**
	 * One figure against the month before. {@code change} = current − previous (percentage points for PERCENT);
	 * {@code changePct} = change ÷ previous × 100 (e.g. leads 180 → 200: +11.11%), null for PERCENT figures, when either
	 * side is missing, or when the previous value is zero. {@code target}: the line comes from a target (shown only to
	 * TARGET_VIEW holders).
	 */
	public record Line(String label, Unit unit, Better better, BigDecimal current, BigDecimal previous, BigDecimal change,
			BigDecimal changePct, boolean target) {

	}

	/** A report section, e.g. "Lead performance"; {@code key} names the module (SEO, LEADS, EMAIL, ...). */
	public record Group(String key, String title, List<Line> lines) {

	}

	/** A keyword's move between the two months; {@code change} = previous − current (positive is better). */
	public record KeywordMove(Long keywordId, String keyword, String page, Integer previousPosition, Integer position,
			Integer change) {

	}

	/** Brief section 50 "keyword movements": the biggest climbs and drops of the month. */
	public record KeywordMovements(List<KeywordMove> improved, List<KeywordMove> declined) {

	}

	/** A target of the month with the same type's target the month before. */
	public record TargetRow(String type, String unit, BigDecimal targetValue, BigDecimal actual,
			BigDecimal achievementPct, BigDecimal remaining, TargetStatus status, BigDecimal previousTargetValue,
			BigDecimal previousActual, BigDecimal previousAchievementPct) {

	}

	/**
	 * {@code frozen}: the report as it was frozen at {@code generatedAt} by {@code generatedBy} (team-wide); otherwise
	 * computed now. {@code ownerId} narrows a live report to one owner.
	 */
	public record MonthlyReport(Period period, Period comparisonPeriod, Long ownerId, boolean frozen, Instant generatedAt,
			UserSummary generatedBy, List<Group> groups, KeywordMovements keywordMovements, List<TargetRow> targets) {

	}

	/** A frozen month in the list of frozen reports. */
	public record FrozenMonth(Period period, Instant generatedAt, UserSummary generatedBy) {

	}

}
