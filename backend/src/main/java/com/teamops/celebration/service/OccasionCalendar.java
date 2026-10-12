package com.teamops.celebration.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.calendar.dto.CalendarDtos.CalendarItem;
import com.teamops.calendar.dto.CalendarDtos.CalendarResponse;
import com.teamops.calendar.dto.CalendarDtos.ItemKind;
import com.teamops.celebration.dto.CelebrationDtos.OccasionInfo;
import com.teamops.celebration.entity.LinkedInPost;
import com.teamops.celebration.entity.MarketingEventStatus;
import com.teamops.celebration.entity.OccasionCategory;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.user.dto.UserSummary;

import lombok.RequiredArgsConstructor;

/**
 * Birthdays, work anniversaries, important days and marketing events as calendar items, with their LinkedIn post
 * status. Every signed-in user sees them. Scheduled email campaigns join them as "Email marketing" for CAMPAIGN_VIEW
 * holders (the Email Campaigns module is part of Digital Marketing), unless an event already links the campaign.
 */
@Service
@RequiredArgsConstructor
public class OccasionCalendar {

	public static final int DEFAULT_UPCOMING_DAYS = 14;

	public static final int MAX_UPCOMING_DAYS = 90;

	private final OccasionFinder finder;

	private final LinkedInPostService linkedInPosts;

	private final EmailCampaignRepository campaignRepository;

	private final BusinessCalendar calendar;

	/** Everything in [from, to] for the calendar, cancelled events included (the UI marks them). */
	@Transactional(readOnly = true)
	public List<CalendarItem> items(LocalDate from, LocalDate to, AuthenticatedUser viewer) {
		List<Occasion> occasions = finder.between(from, to, true);
		List<CalendarItem> items = new ArrayList<>(toItems(occasions));
		if (viewer.isSuperAdmin() || viewer.hasPermission(MarketingEventService.CAMPAIGN_VIEW)) {
			Set<Long> linked = occasions.stream()
				.filter(o -> o.eventStatus() != MarketingEventStatus.CANCELLED)
				.map(Occasion::emailCampaignId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
			campaignRepository.findScheduledBetween(from, to)
				.stream()
				.filter(campaign -> !linked.contains(campaign.getId()))
				.forEach(campaign -> items.add(toItem(campaign)));
		}
		return items;
	}

	/**
	 * The dashboard's "Coming up": birthdays, anniversaries, important days and events from today through the next
	 * {@code days} days (14 by default), without cancelled events.
	 */
	@Transactional(readOnly = true)
	public CalendarResponse upcoming(Integer days) {
		int window = days == null ? DEFAULT_UPCOMING_DAYS : days;
		if (window < 1 || window > MAX_UPCOMING_DAYS) {
			throw ApiException.badRequest("INVALID_RANGE", "'days' must be between 1 and " + MAX_UPCOMING_DAYS);
		}
		LocalDate today = calendar.today();
		LocalDate to = today.plusDays(window - 1L);
		return new CalendarResponse(today, today, to, toItems(finder.between(today, to, false)));
	}

	private List<CalendarItem> toItems(List<Occasion> occasions) {
		Map<String, LinkedInPost> posts = linkedInPosts.forOccasions(occasions);
		return occasions.stream().map(o -> toItem(o, posts.get(o.key()))).toList();
	}

	static CalendarItem toItem(Occasion o, LinkedInPost post) {
		OccasionInfo info = new OccasionInfo(o.category(), o.kind(), o.subjectId(), o.detail(), o.years(),
				o.dayCategory(), o.region(), o.eventType(), o.eventStatus(), o.location(),
				LinkedInPostService.dto(o.kind(), post));
		return new CalendarItem(o.key(), itemKind(o), o.subjectId(), o.title(), null, o.allDay(), o.date(), o.endDate(),
				o.startAt(), o.endAt(), o.department(), o.person(), null, null, null, info);
	}

	private static CalendarItem toItem(EmailCampaign campaign) {
		OccasionInfo info = new OccasionInfo(OccasionCategory.EMAIL_MARKETING, null, campaign.getId(),
				campaign.getAudience(), null, null, null, null, null, null, null);
		return new CalendarItem("CAMPAIGN-" + campaign.getId(), ItemKind.EMAIL_CAMPAIGN, campaign.getId(),
				campaign.getName(), null, true, campaign.getCampaignDate(), campaign.getCampaignDate(), null, null, null,
				UserSummary.of(campaign.getOwner()), null, null, null, info);
	}

	private static ItemKind itemKind(Occasion o) {
		return switch (o.kind()) {
			case BIRTHDAY -> ItemKind.BIRTHDAY;
			case WORK_ANNIVERSARY -> ItemKind.WORK_ANNIVERSARY;
			case IMPORTANT_DAY -> ItemKind.IMPORTANT_DAY;
			case EVENT -> ItemKind.MARKETING_EVENT;
		};
	}

}
