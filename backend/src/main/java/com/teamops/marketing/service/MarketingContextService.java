package com.teamops.marketing.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.dto.MarketingDtos.Integrations;
import com.teamops.marketing.dto.MarketingDtos.MarketingContext;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.integration.AnalyticsProvider;
import com.teamops.marketing.integration.EmailCampaignProvider;
import com.teamops.marketing.integration.LeadProvider;
import com.teamops.marketing.integration.PaidCampaignProvider;
import com.teamops.marketing.integration.SeoRankingProvider;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** The shared context of the Digital Marketing module: period defaults, owners and data sources. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingContextService {

	public static final String MARKETING_VIEW = "MARKETING_VIEW";

	/** Years offered before the current one. Older history stays reachable through the API. */
	static final int YEARS_BACK = 3;

	private final BusinessCalendar calendar;

	private final AppSettingsService settings;

	private final UserRepository userRepository;

	private final SeoRankingProvider seoRankingProvider;

	private final EmailCampaignProvider emailCampaignProvider;

	private final PaidCampaignProvider paidCampaignProvider;

	private final AnalyticsProvider analyticsProvider;

	private final LeadProvider leadProvider;

	public MarketingContext context() {
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		List<Integer> years = IntStream.rangeClosed(current.year() - YEARS_BACK, current.year() + 1)
			.boxed()
			.sorted(Comparator.reverseOrder())
			.toList();
		List<UserSummary> owners = userRepository.findActiveWithPermission(MARKETING_VIEW, UserStatus.ACTIVE)
			.stream()
			.map(UserSummary::of)
			.toList();
		return new MarketingContext(calendar.today(), Period.of(current), years, owners,
				settings.marketingBehindThresholdPct());
	}

	/** The active source for each kind of marketing data (manual entry until an integration is added). */
	public Integrations integrations() {
		return new Integrations(List.of(seoRankingProvider.info(), emailCampaignProvider.info(),
				paidCampaignProvider.info(), analyticsProvider.info(), leadProvider.info()));
	}

}
