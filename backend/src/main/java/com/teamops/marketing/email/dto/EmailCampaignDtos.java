package com.teamops.marketing.email.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.email.entity.CampaignProvider;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.entity.EmailCampaignStatus;
import com.teamops.marketing.email.entity.EmailCampaignType;
import com.teamops.marketing.email.service.EmailCounts;
import com.teamops.marketing.email.service.EmailRates;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Email campaign API records. Rates are computed per request from the counts. */
public final class EmailCampaignDtos {

	private static final int MAX_COUNT = 100_000_000;

	private EmailCampaignDtos() {
	}

	public record CampaignItem(Long id, String name, EmailCampaignType campaignType, LocalDate campaignDate,
			UserSummary owner, String audience, EmailCampaignStatus status, EmailCounts counts, EmailRates rates,
			String notes, CampaignProvider provider, String externalId, Integer version, Instant createdAt,
			Instant updatedAt) {

		public static CampaignItem of(EmailCampaign campaign) {
			EmailCounts counts = campaign.counts();
			return new CampaignItem(campaign.getId(), campaign.getName(), campaign.getCampaignType(),
					campaign.getCampaignDate(), UserSummary.of(campaign.getOwner()), campaign.getAudience(),
					campaign.getStatus(), counts, EmailRates.of(counts), campaign.getNotes(), campaign.getProvider(),
					campaign.getExternalId(), campaign.getVersion(), campaign.getCreatedAt(), campaign.getUpdatedAt());
		}

	}

	/**
	 * Create or (with {@code version}) update. Counts default to 0 and may only be non-zero for a SENT campaign; a
	 * sent campaign cannot be dated in the future.
	 */
	public record SaveCampaign(
			Integer version,
			@NotBlank(message = "Name is required") @Size(max = 200, message = "At most 200 characters") String name,
			@NotNull(message = "Campaign type is required") EmailCampaignType campaignType,
			@NotNull(message = "Campaign date is required") LocalDate campaignDate,
			Long ownerId,
			@Size(max = 200, message = "At most 200 characters") String audience,
			@NotNull(message = "Status is required") EmailCampaignStatus status,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer emailsSent,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer delivered,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer bounced,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer opened,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer uniqueOpens,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer clicked,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer uniqueClicks,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer unsubscribed,
			@Min(value = 0, message = "Cannot be negative") @Max(value = MAX_COUNT, message = "Too large") Integer leadsGenerated,
			@Size(max = 2000, message = "At most 2000 characters") String notes) {

		public EmailCounts counts() {
			return new EmailCounts(zero(emailsSent), zero(delivered), zero(bounced), zero(opened), zero(uniqueOpens),
					zero(clicked), zero(uniqueClicks), zero(unsubscribed), zero(leadsGenerated));
		}

		private static int zero(Integer value) {
			return value == null ? 0 : value;
		}

	}

	/** One month's sent campaigns: how many, their summed counts, and the rates of the sums. */
	public record MonthTotals(Period period, int campaigns, EmailCounts counts, EmailRates rates) {

	}

	public record TypeTotals(EmailCampaignType campaignType, int campaigns, EmailCounts counts, EmailRates rates) {

	}

	/** The monthly summary (brief section 39) compared with another month (the previous one by default). */
	public record MonthlySummary(MonthTotals current, MonthTotals comparison, List<TypeTotals> byType) {

	}

	/** The monthly email trend, oldest month first; months without a sent campaign have zero counts. */
	public record EmailTrend(List<MonthTotals> months) {

	}

}
