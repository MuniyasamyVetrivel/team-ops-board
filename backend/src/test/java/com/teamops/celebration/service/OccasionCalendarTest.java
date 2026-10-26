package com.teamops.celebration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.celebration.entity.OccasionKind;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.support.SliceAuth;

/** Celebrations belong to Digital Marketing: the shared calendar shows them only to Super Admins and MARKETING_VIEW. */
class OccasionCalendarTest {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);

	private static final LocalDate TO = LocalDate.of(2026, 10, 31);

	private final OccasionFinder finder = mock(OccasionFinder.class);

	private final LinkedInPostService linkedInPosts = mock(LinkedInPostService.class);

	private final EmailCampaignRepository campaigns = mock(EmailCampaignRepository.class);

	private final OccasionCalendar calendar = new OccasionCalendar(finder, linkedInPosts, campaigns,
			new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-13T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"));

	private final Occasion birthday = new Occasion(OccasionKind.BIRTHDAY, 1L,
			LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14), "Priya Menon", "Priya Menon's birthday", null, null,
			null, null, null, null, null, null, null, null, null, null);

	@Test
	void usersOutsideDigitalMarketingGetNoOccasions() {
		for (AuthenticatedUser user : List.of(SliceAuth.EMPLOYEE, SliceAuth.NOBODY, new AuthenticatedUser(13L,
				"hr.user@teamops.local", "HR User", 3L, Set.of("EMPLOYEE"),
				SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "CELEBRATIONS_EDIT")))) {
			assertThat(calendar.items(FROM, TO, user)).isEmpty();
		}
		verifyNoInteractions(finder, linkedInPosts, campaigns);
	}

	@Test
	void digitalMarketingUsersAndSuperAdminsSeeThem() {
		when(finder.between(FROM, TO, true)).thenReturn(List.of(birthday));
		when(linkedInPosts.forOccasions(anyCollection())).thenReturn(Map.of());
		AuthenticatedUser marketer = new AuthenticatedUser(12L, "arun.kumar@teamops.local", "Arun Kumar", 7L,
				Set.of("EMPLOYEE"), SliceAuth.with(SliceAuth.EMPLOYEE_PERMISSIONS, "MARKETING_VIEW"));
		for (AuthenticatedUser user : List.of(marketer, SliceAuth.SUPER_ADMIN)) {
			assertThat(calendar.items(FROM, TO, user)).extracting(item -> item.title())
				.contains("Priya Menon's birthday");
		}
	}

}
