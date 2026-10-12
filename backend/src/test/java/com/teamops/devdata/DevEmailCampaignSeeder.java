package com.teamops.devdata;

import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.marketing.email.entity.CampaignProvider;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.entity.EmailCampaignStatus;
import com.teamops.marketing.email.entity.EmailCampaignType;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.marketing.email.service.EmailCounts;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Email campaigns over the last three months (brief section 77: SAP Testing Services Outreach, 25,000 sent, 24,000
 * delivered, 8,500 unique opens, 1,250 unique clicks, 185 leads), plus a scheduled and a draft campaign. Dates are
 * relative to today and never in the future for sent campaigns. Runs only while there are no campaigns.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevEmailCampaignSeeder {

	private final EmailCampaignRepository campaignRepository;

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
		LocalDate thisMonth = today.withDayOfMonth(1);
		LocalDate lastMonth = thisMonth.minusMonths(1);
		LocalDate twoAgo = thisMonth.minusMonths(2);
		int created = 0;

		// Two months ago.
		created += sent("Quarterly newsletter", EmailCampaignType.NEWSLETTER, twoAgo.plusDays(4), "All subscribers", priya,
				new EmailCounts(18_000, 17_300, 700, 6_100, 5_400, 820, 700, 52, 41), "ZC-0901");
		created += sent("Test automation webinar invite", EmailCampaignType.EVENT, twoAgo.plusDays(15), "QA leaders", priya,
				new EmailCounts(6_000, 5_820, 180, 2_300, 2_050, 410, 360, 14, 38), "ZC-0907");

		// Last month.
		created += sent("Monthly newsletter", EmailCampaignType.NEWSLETTER, lastMonth.plusDays(3), "All subscribers", priya,
				new EmailCounts(19_500, 18_900, 600, 6_900, 6_150, 900, 780, 61, 48), "ZC-0931");
		created += sent("Performance testing services", EmailCampaignType.LEAD_GENERATION, lastMonth.plusDays(12),
				"IT managers, India", priya, new EmailCounts(12_000, 11_520, 480, 4_000, 3_620, 610, 540, 33, 92), "ZC-0944");
		created += sent("QA engineer hiring", EmailCampaignType.RECRUITMENT, lastMonth.plusDays(20), "Talent pool", priya,
				new EmailCounts(3_000, 2_940, 60, 1_300, 1_180, 260, 230, 9, 0), "ZC-0952");

		// This month (the brief's sample campaign first), never after today.
		created += sent("SAP Testing Services Outreach", EmailCampaignType.LEAD_GENERATION, earliest(thisMonth.plusDays(1), today),
				"SAP decision makers", priya, new EmailCounts(25_000, 24_000, 1_000, 9_400, 8_500, 1_400, 1_250, 60, 185),
				"ZC-1001");
		created += sent("Monthly newsletter", EmailCampaignType.NEWSLETTER, earliest(thisMonth.plusDays(4), today),
				"All subscribers", priya, new EmailCounts(20_100, 19_450, 650, 7_000, 6_300, 950, 820, 58, 52), "ZC-1008");
		created += other("Test automation ROI calculator launch", EmailCampaignType.PRODUCT_PROMOTION,
				today.plusDays(7), EmailCampaignStatus.SCHEDULED, priya);
		created += other("Year-end customer update", EmailCampaignType.NEWSLETTER, today.plusDays(30),
				EmailCampaignStatus.DRAFT, priya);
		return created;
	}

	private int sent(String name, EmailCampaignType type, LocalDate date, String audience, User owner, EmailCounts counts,
			String externalId) {
		EmailCampaign campaign = base(name, type, date, EmailCampaignStatus.SENT, owner);
		campaign.setAudience(audience);
		campaign.apply(counts);
		campaign.setProvider(CampaignProvider.CSV);
		campaign.setExternalId(externalId);
		campaignRepository.save(campaign);
		return 1;
	}

	private int other(String name, EmailCampaignType type, LocalDate date, EmailCampaignStatus status, User owner) {
		campaignRepository.save(base(name, type, date, status, owner));
		return 1;
	}

	private static EmailCampaign base(String name, EmailCampaignType type, LocalDate date, EmailCampaignStatus status,
			User owner) {
		EmailCampaign campaign = new EmailCampaign();
		campaign.setName(name);
		campaign.setCampaignType(type);
		campaign.setCampaignDate(date);
		campaign.setStatus(status);
		campaign.setOwner(owner);
		return campaign;
	}

	private static LocalDate earliest(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

}
