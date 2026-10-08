package com.teamops.marketing.lead.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.lead.entity.LeadOrigin;
import com.teamops.marketing.lead.entity.LeadStatus;
import com.teamops.marketing.lead.repository.LeadQuery.LinkKind;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Marketing lead API records. Counts, shares and target progress are computed per request. */
public final class LeadDtos {

	private LeadDtos() {
	}

	/** The campaign or content that brought the lead in; {@code date} is when it was sent, started or published. */
	public record LeadLink(LinkKind kind, Long id, String name, LocalDate date) {

	}

	/**
	 * A lead. {@code countLocked}: its month is closed for the viewer, so the fields that count towards targets (date
	 * and source) are fixed and it cannot be deleted; contact details, status, owner, link and notes stay editable.
	 */
	public record LeadItem(Long id, String code, String name, String company, String email, String phone,
			LeadSource source, LeadLink link, DepartmentSummary department, LocalDate leadDate, LeadStatus status,
			UserSummary owner, String notes, LeadOrigin provider, String externalId, UserSummary createdBy,
			boolean countLocked, Integer version, Instant createdAt, Instant updatedAt) {

	}

	/** Create or (with {@code version}) update a lead. At most one of the three links, matching the source. */
	public record SaveLead(
			Integer version,
			@NotBlank(message = "Name is required") @Size(max = 200, message = "At most 200 characters") String name,
			@Size(max = 200, message = "At most 200 characters") String company,
			@Email(message = "Enter a valid email address") @Size(max = 255, message = "At most 255 characters") String email,
			@Size(max = 50, message = "At most 50 characters") String phone,
			@NotNull(message = "Source is required") LeadSource source,
			Long emailCampaignId,
			Long paidCampaignId,
			Long contentItemId,
			Long departmentId,
			@NotNull(message = "Lead date is required") LocalDate leadDate,
			LeadStatus status,
			Long ownerId,
			@Size(max = 2000, message = "At most 2000 characters") String notes) {

	}

	public record ChangeStatus(@NotNull(message = "Version is required") Integer version,
			@NotNull(message = "Status is required") LeadStatus status) {

	}

	/** A campaign or content item a lead can be linked to. */
	public record LinkOption(LinkKind kind, Long id, String name, LocalDate date, String detail) {

	}

	/** One source in a month: its leads, the comparison month's, and its target when the viewer may see targets. */
	public record SourceCount(LeadSource source, long leads, long comparison, TargetItem target) {

	}

	public record StatusCount(LeadStatus status, long leads) {

	}

	public record LinkCount(LinkKind kind, Long id, String name, long leads) {

	}

	/**
	 * Brief section 44: the month's leads by source against their targets (the Website Leads target for the total),
	 * by status, and the campaigns and content that brought the most in. Targets are null unless the viewer has
	 * TARGET_VIEW ({@code targetsVisible}).
	 */
	public record LeadSummary(Period period, Period comparisonPeriod, long total, long comparisonTotal,
			BigDecimal convertedPct, boolean targetsVisible, TargetItem totalTarget, List<SourceCount> bySource,
			List<StatusCount> byStatus, List<LinkCount> topLinks) {

	}

	/** One month: all leads and leads per source (every source present, zeros included). */
	public record MonthCounts(Period period, long total, Map<LeadSource, Long> bySource) {

	}

	public record LeadTrend(List<MonthCounts> months) {

	}

}
