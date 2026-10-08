package com.teamops.devdata;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.paid.entity.AdDataSource;
import com.teamops.marketing.paid.entity.AdPlatform;
import com.teamops.marketing.paid.entity.CampaignObjective;
import com.teamops.marketing.paid.entity.PaidCampaign;
import com.teamops.marketing.paid.entity.PaidCampaignMonth;
import com.teamops.marketing.paid.entity.PaidCampaignStatus;
import com.teamops.marketing.paid.repository.PaidCampaignMonthRepository;
import com.teamops.marketing.paid.repository.PaidCampaignRepository;
import com.teamops.marketing.paid.service.PaidResults;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Paid campaigns (brief sections 42 and 76: SAP S/4HANA Testing Campaign on LinkedIn, budget ₹50,000, spent ₹42,000,
 * 150,000 impressions, 2,800 clicks, 84 leads this month), plus a finished two-month campaign, a paused one and a
 * draft. Dates are relative to today. Runs only while there are no campaigns.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevPaidCampaignSeeder {

	private final PaidCampaignRepository campaignRepository;

	private final PaidCampaignMonthRepository monthRepository;

	private final UserRepository userRepository;

	private final BusinessCalendar calendar;

	/** @return the number of campaigns created (0 when campaigns already exist) */
	@Transactional
	public int seedIfEmpty() {
		if (campaignRepository.count() > 0) {
			return 0;
		}
		User priya = userRepository.findByEmailIgnoreCase("priya.menon@teamops.local").orElse(null);
		LocalDate today = calendar.today();
		MarketingPeriod thisMonth = MarketingPeriod.of(today);
		MarketingPeriod lastMonth = thisMonth.previous();
		MarketingPeriod twoAgo = lastMonth.previous();

		PaidCampaign sap = campaign("SAP S/4HANA Testing Campaign", CampaignObjective.LEAD_GENERATION, thisMonth.firstDay(),
				thisMonth.lastDay(), "50000", PaidCampaignStatus.ACTIVE, priya, "LI-508812");
		month(sap, thisMonth, new PaidResults(new BigDecimal("42000"), 150_000, 2_800, 84, 21), priya);

		PaidCampaign webinar = campaign("Test automation webinar promotion", CampaignObjective.WEBSITE_CONVERSIONS,
				twoAgo.firstDay(), lastMonth.lastDay(), "60000", PaidCampaignStatus.COMPLETED, priya, "LI-507730");
		month(webinar, twoAgo, new PaidResults(new BigDecimal("27500"), 96_000, 1_350, 41, 12), priya);
		month(webinar, lastMonth, new PaidResults(new BigDecimal("29800"), 104_500, 1_620, 52, 17), priya);

		PaidCampaign brand = campaign("QA services brand awareness", CampaignObjective.BRAND_AWARENESS, lastMonth.firstDay(),
				null, "40000", PaidCampaignStatus.PAUSED, priya, "LI-508101");
		month(brand, lastMonth, new PaidResults(new BigDecimal("18200"), 210_000, 1_050, 9, 2), priya);

		campaign("Performance testing lead gen (Q-next)", CampaignObjective.LEAD_GENERATION, thisMonth.next().firstDay(),
				thisMonth.next().lastDay(), "45000", PaidCampaignStatus.DRAFT, priya, null);
		return 4;
	}

	private PaidCampaign campaign(String name, CampaignObjective objective, LocalDate start, LocalDate end, String budget,
			PaidCampaignStatus status, User owner, String externalId) {
		PaidCampaign campaign = new PaidCampaign();
		campaign.setName(name);
		campaign.setPlatform(AdPlatform.LINKEDIN);
		campaign.setObjective(objective);
		campaign.setStartDate(start);
		campaign.setEndDate(end);
		campaign.setBudget(new BigDecimal(budget).setScale(2));
		campaign.setStatus(status);
		campaign.setOwner(owner);
		if (externalId != null) {
			campaign.setProvider(AdDataSource.CSV);
			campaign.setExternalId(externalId);
		}
		return campaignRepository.save(campaign);
	}

	private void month(PaidCampaign campaign, MarketingPeriod period, PaidResults results, User recordedBy) {
		PaidCampaignMonth row = new PaidCampaignMonth();
		row.setCampaign(campaign);
		row.setMonth(period.month());
		row.setYear(period.year());
		row.apply(results);
		row.setSource(AdDataSource.CSV);
		row.setRecordedBy(recordedBy);
		monthRepository.save(row);
	}

}
