package com.teamops.marketing.backlink.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.marketing.backlink.entity.BacklinkOrigin;
import com.teamops.marketing.backlink.entity.BacklinkStatus;
import com.teamops.marketing.backlink.entity.BacklinkType;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Backlink API records. Monthly counts, remaining and achievement are computed per request. */
public final class BacklinkDtos {

	private BacklinkDtos() {
	}

	/** The SEO page a backlink points to. */
	public record PageRef(Long id, String title, String url) {

	}

	/**
	 * A backlink. {@code lockedDates}: the stage date fields whose month is closed for the viewer (the current and
	 * previous month are open, a Super Admin may change any past month); those dates count in closed months and stay
	 * as they are. Other fields and the remaining stages stay editable.
	 */
	public record BacklinkItem(Long id, String code, PageRef targetPage, String targetUrl, String referringDomain,
			String linkUrl, String anchorText, BacklinkType linkType, BacklinkStatus status, LocalDate submittedDate,
			LocalDate approvedDate, LocalDate liveDate, LocalDate rejectedDate, LocalDate lostDate, UserSummary owner,
			Integer domainAuthority, String notes, BacklinkOrigin provider, UserSummary createdBy,
			List<String> lockedDates, Integer version, Instant createdAt, Instant updatedAt) {

	}

	/**
	 * Create or (with {@code version}) update a backlink. The target is an SEO page or a URL / site path (the page's
	 * URL when only the page is given); the referring domain defaults to the link URL's host. The status must have
	 * the dates of the stages it went through ({@code BacklinkRules}).
	 */
	public record SaveBacklink(
			Integer version,
			Long targetPageId,
			@Size(max = 500, message = "At most 500 characters") String targetUrl,
			@Size(max = 255, message = "At most 255 characters") String referringDomain,
			@Size(max = 700, message = "At most 700 characters") String linkUrl,
			@Size(max = 255, message = "At most 255 characters") String anchorText,
			@NotNull(message = "Type is required") BacklinkType linkType,
			BacklinkStatus status,
			LocalDate submittedDate,
			LocalDate approvedDate,
			LocalDate liveDate,
			LocalDate rejectedDate,
			LocalDate lostDate,
			Long ownerId,
			@Min(value = 0, message = "Between 0 and 100") @Max(value = 100, message = "Between 0 and 100") Integer domainAuthority,
			@Size(max = 2000, message = "At most 2000 characters") String notes) {

	}

	/**
	 * Move a backlink to a status: the stages it needs and has not reached are dated {@code date} (today when
	 * omitted), and stages the status does not allow are cleared.
	 */
	public record ChangeStatus(@NotNull(message = "Version is required") Integer version,
			@NotNull(message = "Status is required") BacklinkStatus status, LocalDate date) {

	}

	/** One month's activity: backlinks submitted, approved, gone live, rejected and lost in it. */
	public record MonthActivity(Period period, long submitted, long approved, long live, long rejected, long lost) {

	}

	/**
	 * Brief section 46 for one month: the Backlinks target against what was submitted, approved and went live.
	 * {@code remaining} is what is still to be submitted, max(target − submitted, 0) (50 − 35 = 15); the target's own
	 * achievement counts live links ({@code target.actual}, 22 of 50). Null when there is no target or the viewer
	 * lacks TARGET_VIEW.
	 */
	public record MonthTarget(BigDecimal targetValue, BigDecimal remaining, TargetItem target) {

	}

	public record TypeCount(BacklinkType linkType, long live) {

	}

	public record StatusCount(BacklinkStatus status, long backlinks) {

	}

	public record OwnerActivity(UserSummary owner, long submitted, long approved, long live) {

	}

	/**
	 * The month against the comparison month, its target (TARGET_VIEW only, {@code targetsVisible}), live links by
	 * type, activity by owner, and the pipeline today (backlinks per current status).
	 */
	public record BacklinkSummary(MonthActivity current, MonthActivity comparison, boolean targetsVisible,
			MonthTarget target, List<TypeCount> liveByType, List<OwnerActivity> byOwner, List<StatusCount> pipeline) {

	}

	/** The monthly history: each month's activity with its target and remaining (null without a target). */
	public record TrendMonth(MonthActivity activity, BigDecimal targetValue, BigDecimal remaining,
			BigDecimal liveAchievementPct) {

	}

	public record BacklinkTrend(boolean targetsVisible, List<TrendMonth> months) {

	}

}
