package com.teamops.marketing.content.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.marketing.content.entity.ContentStatus;
import com.teamops.marketing.content.entity.ContentType;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Content API records. Monthly counts, remaining, achievement and lead counts are computed per request. */
public final class ContentDtos {

	private ContentDtos() {
	}

	public record PageRef(Long id, String title, String url) {

	}

	public record KeywordRef(Long id, String keyword) {

	}

	/**
	 * A content item. {@code leads}: all-time leads naming it. {@code publicationLocked}: it is live and its
	 * publication month is closed for the viewer, so it stays published in that month (no new publication date, type
	 * or unpublishing). {@code refreshLocked} likewise for an UPDATED item's refreshed date.
	 */
	public record ContentItemDto(Long id, String title, String url, ContentType contentType, ContentStatus status,
			UserSummary author, UserSummary owner, LocalDate plannedDate, LocalDate publicationDate,
			LocalDate refreshedDate, KeywordRef targetKeyword, String targetKeywordText, PageRef targetPage,
			Integer organicTraffic, Integer ctaClicks, String notes, long leads, boolean publicationLocked,
			boolean refreshLocked, UserSummary createdBy, Integer version, Instant createdAt, Instant updatedAt) {

	}

	/**
	 * Create or (with {@code version}) update a content item. Live content needs its URL and publication date
	 * ({@code ContentRules}); the target keyword text defaults to the chosen SEO keyword.
	 */
	public record SaveContent(
			Integer version,
			@NotBlank(message = "Title is required") @Size(max = 300, message = "At most 300 characters") String title,
			@Size(max = 1000, message = "At most 1000 characters") String url,
			@NotNull(message = "Type is required") ContentType contentType,
			ContentStatus status,
			Long authorId,
			Long ownerId,
			LocalDate plannedDate,
			LocalDate publicationDate,
			LocalDate refreshedDate,
			Long targetKeywordId,
			@Size(max = 255, message = "At most 255 characters") String targetKeywordText,
			Long targetPageId,
			@Min(value = 0, message = "Cannot be negative") @Max(value = 1_000_000_000, message = "Too large") Integer organicTraffic,
			@Min(value = 0, message = "Cannot be negative") @Max(value = 1_000_000_000, message = "Too large") Integer ctaClicks,
			@Size(max = 2000, message = "At most 2000 characters") String notes) {

	}

	/**
	 * Move an item to a status: publishing dates it {@code date} (today when omitted) unless it already has a
	 * publication date, updating dates the refresh, earlier stages clear both dates.
	 */
	public record ChangeStatus(@NotNull(message = "Version is required") Integer version,
			@NotNull(message = "Status is required") ContentStatus status, LocalDate date) {

	}

	/**
	 * One month: blogs planned for it, blogs and all content published in it, items refreshed in it, and leads that
	 * named content.
	 */
	public record MonthFigures(Period period, long plannedBlogs, long publishedBlogs, long publishedAll, long refreshed,
			long leads) {

	}

	/**
	 * Brief section 48 for one month: the blog target against blogs published ({@code target.actual}), with what is
	 * left, max(target − published, 0) (12 − 9 = 3). Null when there is no target or the viewer lacks TARGET_VIEW.
	 */
	public record BlogTarget(BigDecimal targetValue, BigDecimal remaining, TargetItem target) {

	}

	public record TypeCount(ContentType contentType, long published) {

	}

	public record StatusCount(ContentStatus status, long items) {

	}

	public record TopContent(Long id, String title, ContentType contentType, String url, Integer organicTraffic,
			Integer ctaClicks, long leads) {

	}

	/**
	 * The month against the comparison month, the blog target (and the Blog Leads target) for TARGET_VIEW holders
	 * ({@code targetsVisible}), content published by type, today's pipeline, and the content with the most leads.
	 */
	public record ContentSummary(MonthFigures current, MonthFigures comparison, boolean targetsVisible,
			BlogTarget blogTarget, TargetItem blogLeadsTarget, List<TypeCount> publishedByType, List<StatusCount> pipeline,
			List<TopContent> topContent) {

	}

	/** Monthly history: each month's figures with its blog target, remaining and achievement (null without a target). */
	public record TrendMonth(MonthFigures figures, BigDecimal targetValue, BigDecimal remaining,
			BigDecimal achievementPct) {

	}

	public record ContentTrend(boolean targetsVisible, List<TrendMonth> months) {

	}

}
