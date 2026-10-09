package com.teamops.marketing.content.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.content.entity.ContentStatus;

/**
 * The publishing rules of a content item (brief sections 47–48). Live content (PUBLISHED or UPDATED) has its URL and
 * publication date, which is never in the future (a post scheduled for later is still a draft with a planned date);
 * an UPDATED item also has the date it was refreshed, on or after publication. Content that is not live has neither
 * date, so the monthly counts only ever see live content.
 */
public final class ContentRules {

	/** The dates that decide where a content item counts. */
	public record Dates(LocalDate published, LocalDate refreshed) {

		public static final Dates NONE = new Dates(null, null);

	}

	private ContentRules() {
	}

	public static void check(ContentStatus status, Dates dates, String url, LocalDate today) {
		if (status.isLive()) {
			if (dates.published() == null) {
				throw ApiException.badRequest("MISSING_DATE", "Published content needs its publication date");
			}
			if (url == null) {
				throw ApiException.badRequest("CONTENT_URL_REQUIRED", "Published content needs its URL");
			}
		}
		else if (dates.published() != null || dates.refreshed() != null) {
			throw ApiException.badRequest("DATE_NOT_ALLOWED",
					"Only published content has a publication date; plan the month with the planned date");
		}
		if (status == ContentStatus.UPDATED && dates.refreshed() == null) {
			throw ApiException.badRequest("MISSING_DATE", "Updated content needs the date it was refreshed");
		}
		if (status == ContentStatus.PUBLISHED && dates.refreshed() != null) {
			throw ApiException.badRequest("DATE_NOT_ALLOWED", "Only updated content has a refreshed date");
		}
		if (dates.published() != null && dates.published().isAfter(today)) {
			throw ApiException.badRequest("FUTURE_DATE",
					"The publication date cannot be in the future; keep the item as a draft with a planned date");
		}
		if (dates.refreshed() != null && dates.refreshed().isAfter(today)) {
			throw ApiException.badRequest("FUTURE_DATE", "The refreshed date cannot be in the future");
		}
		if (dates.published() != null && dates.refreshed() != null && dates.refreshed().isBefore(dates.published())) {
			throw ApiException.badRequest("DATE_ORDER", "The refreshed date cannot be before the publication date");
		}
	}

	/**
	 * The dates after a move to {@code status} on {@code date}: publishing dates it (keeping an existing publication
	 * date), updating dates the refresh, and going back to a stage before publishing clears both.
	 */
	public static Dates moveTo(ContentStatus status, Dates current, LocalDate date) {
		if (!status.isLive()) {
			return Dates.NONE;
		}
		LocalDate published = current.published() == null ? date : current.published();
		return status == ContentStatus.UPDATED ? new Dates(published, date) : new Dates(published, null);
	}

	/** Brief section 48: max(target − published, 0), e.g. 12 − 9 = 3. */
	public static BigDecimal remaining(BigDecimal target, long published) {
		return MarketingMath.remaining(target, published);
	}

}
