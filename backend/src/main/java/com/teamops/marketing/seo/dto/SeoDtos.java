package com.teamops.marketing.seo.dto;

import java.time.Instant;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.entity.PageStatus;
import com.teamops.marketing.seo.entity.PageType;
import com.teamops.marketing.seo.entity.SearchEngine;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.seo.service.KeywordStanding;
import com.teamops.marketing.seo.service.SeoStats;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** SEO page and keyword API records. Positions, statuses and statistics are computed for the requested month. */
public final class SeoDtos {

	/** A site path ("/services/sap-testing") or a full http(s) URL, without spaces. */
	public static final String URL_PATTERN = "^(/|https?://)\\S*$";

	private SeoDtos() {
	}

	public record PageRef(Long id, String title, String url, PageStatus status) {

		public static PageRef of(SeoPage page) {
			return new PageRef(page.getId(), page.getTitle(), page.getUrl(), page.getStatus());
		}

	}

	public record PageListItem(Long id, String url, String title, PageType pageType, String primaryKeyword,
			DepartmentSummary department, UserSummary owner, PageStatus status, SeoStats stats, Instant updatedAt) {

		public static PageListItem of(SeoPage page, SeoStats stats) {
			return new PageListItem(page.getId(), page.getUrl(), page.getTitle(), page.getPageType(),
					page.getPrimaryKeyword(), DepartmentSummary.of(page.getDepartment()), UserSummary.of(page.getOwner()),
					page.getStatus(), stats, page.getUpdatedAt());
		}

	}

	public record SeoPermissions(boolean canEdit) {

	}

	/**
	 * A page with its statistics for {@code period} and for the month before it (for the comparison on the KPI
	 * cards). Keywords are listed through {@code GET /api/marketing/keywords?pageId=}.
	 */
	public record PageDetail(Long id, String url, String title, PageType pageType, String primaryKeyword,
			DepartmentSummary department, UserSummary owner, PageStatus status, Period period, SeoStats stats,
			Period previousPeriod, SeoStats previousStats, long keywordCount, Integer version, Instant createdAt,
			Instant updatedAt, SeoPermissions permissions) {

	}

	public record CreatePage(
			@NotBlank(message = "URL is required") @Size(max = 500, message = "At most 500 characters")
			@Pattern(regexp = URL_PATTERN, message = "Enter a path starting with / or a full http(s) URL, without spaces") String url,
			@NotBlank(message = "Title is required") @Size(max = 200, message = "At most 200 characters") String title,
			@NotNull(message = "Page type is required") PageType pageType,
			@Size(max = 200, message = "At most 200 characters") String primaryKeyword,
			@NotNull(message = "Department is required") Long departmentId,
			Long ownerId) {

	}

	/** Full replacement of the editable fields. */
	public record UpdatePage(
			@NotNull(message = "Version is required") Integer version,
			@NotBlank(message = "URL is required") @Size(max = 500, message = "At most 500 characters")
			@Pattern(regexp = URL_PATTERN, message = "Enter a path starting with / or a full http(s) URL, without spaces") String url,
			@NotBlank(message = "Title is required") @Size(max = 200, message = "At most 200 characters") String title,
			@NotNull(message = "Page type is required") PageType pageType,
			@Size(max = 200, message = "At most 200 characters") String primaryKeyword,
			@NotNull(message = "Department is required") Long departmentId,
			Long ownerId,
			@NotNull(message = "Status is required") PageStatus status) {

	}

	/** A keyword with its standing in the requested month. */
	public record KeywordItem(Long id, String keyword, PageRef page, SearchEngine searchEngine, String location,
			Device device, Integer targetPosition, Integer searchVolume, Integer keywordDifficulty, UserSummary owner,
			KeywordStatus status, KeywordStanding ranking, Instant lastRankedAt, Integer version, Instant createdAt,
			Instant updatedAt) {

		public static KeywordItem of(SeoKeyword keyword, KeywordStanding ranking) {
			return new KeywordItem(keyword.getId(), keyword.getKeyword(), PageRef.of(keyword.getPage()),
					keyword.getSearchEngine(), keyword.getLocation(), keyword.getDevice(), keyword.getTargetPosition(),
					keyword.getSearchVolume(), keyword.getKeywordDifficulty(), UserSummary.of(keyword.getOwner()),
					keyword.getStatus(), ranking, keyword.getLastRankedAt(), keyword.getVersion(),
					keyword.getCreatedAt(), keyword.getUpdatedAt());
		}

	}

	/** {@code searchEngine}, {@code location} and {@code device} default to Google, India and desktop. */
	public record CreateKeyword(
			@NotNull(message = "Page is required") Long pageId,
			@NotBlank(message = "Keyword is required") @Size(max = 200, message = "At most 200 characters") String keyword,
			SearchEngine searchEngine,
			@Size(max = 100, message = "At most 100 characters") String location,
			Device device,
			@Min(value = 1, message = "1–100") @Max(value = 100, message = "1–100") Integer targetPosition,
			@Min(value = 0, message = "Cannot be negative") @Max(value = 1_000_000_000, message = "Too large") Integer searchVolume,
			@Min(value = 0, message = "0–100") @Max(value = 100, message = "0–100") Integer keywordDifficulty,
			Long ownerId) {

	}

	public record UpdateKeyword(
			@NotNull(message = "Version is required") Integer version,
			@NotNull(message = "Page is required") Long pageId,
			@NotBlank(message = "Keyword is required") @Size(max = 200, message = "At most 200 characters") String keyword,
			SearchEngine searchEngine,
			@Size(max = 100, message = "At most 100 characters") String location,
			Device device,
			@Min(value = 1, message = "1–100") @Max(value = 100, message = "1–100") Integer targetPosition,
			@Min(value = 0, message = "Cannot be negative") @Max(value = 1_000_000_000, message = "Too large") Integer searchVolume,
			@Min(value = 0, message = "0–100") @Max(value = 100, message = "0–100") Integer keywordDifficulty,
			Long ownerId,
			@NotNull(message = "Status is required") KeywordStatus status) {

	}

}
