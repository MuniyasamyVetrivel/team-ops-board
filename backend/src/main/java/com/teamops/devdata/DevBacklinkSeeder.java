package com.teamops.devdata;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.marketing.backlink.entity.Backlink;
import com.teamops.marketing.backlink.entity.BacklinkDates;
import com.teamops.marketing.backlink.entity.BacklinkOrigin;
import com.teamops.marketing.backlink.entity.BacklinkStatus;
import com.teamops.marketing.backlink.entity.BacklinkType;
import com.teamops.marketing.backlink.repository.BacklinkRepository;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.seo.entity.PageStatus;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Backlinks for this month and the two before it, matching the seeded Backlinks targets (38 and 41 live, then brief
 * section 74 this month: 35 submitted, 28 approved, 22 live, so 15 still to submit against the target of 50). This
 * month also has 2 rejections and one link from two months ago lost; 6 prospects have no dates yet. Dates are
 * relative to today and never in the future. Runs only while there are no backlinks.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevBacklinkSeeder {

	private static final String[] DOMAINS = { "dzone.com", "techbullion.com", "softwaretestingmagazine.com",
			"testingtoolsguide.net", "qa-platforms.com", "sapinsider.org", "devops.com", "clutch.co", "goodfirms.co",
			"medium.com", "hashnode.dev", "itbusinessedge.com", "techgenix.com", "softwaresuggest.com", "g2.com",
			"yourstory.com", "inc42.com", "analyticsindiamag.com", "techcircle.in", "stickyminds.com" };

	private static final BacklinkType[] TYPES = { BacklinkType.GUEST_POST, BacklinkType.DIRECTORY,
			BacklinkType.BUSINESS_LISTING, BacklinkType.GUEST_POST, BacklinkType.RESOURCE_PAGE, BacklinkType.PROFILE,
			BacklinkType.PRESS_RELEASE };

	private static final String[] ANCHORS = { "SAP testing services", "software testing company", "test automation",
			"QA outsourcing", "banking QA", "software testing company in Chennai" };

	/** Per month offset (-2, -1, 0): live, still approved, still submitted, rejected. */
	private static final int[][] PLAN = { { 38, 0, 0, 4 }, { 41, 2, 0, 3 }, { 22, 8, 3, 2 } };

	/** This month: 2 of the 22 live links went live without a separate approval (28 approved = 20 + 8). */
	private static final int DIRECT_LIVE_THIS_MONTH = 2;

	private static final int PROSPECTS = 6;

	private final BacklinkRepository backlinkRepository;

	private final SeoPageRepository pageRepository;

	private final UserRepository userRepository;

	private final CodeGenerator codeGenerator;

	private final BusinessCalendar calendar;

	private int counter;

	/** @return the number of backlinks created (0 when backlinks already exist) */
	@Transactional
	public int seedIfEmpty() {
		if (backlinkRepository.count() > 0) {
			return 0;
		}
		LocalDate today = calendar.today();
		MarketingPeriod current = MarketingPeriod.of(today);
		List<SeoPage> pages = pageRepository.findByStatusNotOrderByTitleAsc(PageStatus.ARCHIVED);
		List<User> owners = List.of("arun.kumar@teamops.local", "priya.menon@teamops.local")
			.stream()
			.map(email -> userRepository.findByEmailIgnoreCase(email).orElse(null))
			.filter(u -> u != null)
			.toList();
		counter = 0;
		for (int offset = -2; offset <= 0; offset++) {
			MarketingPeriod period = current.plusMonths(offset);
			int lastDay = offset == 0 ? today.getDayOfMonth() : Math.min(28, period.lastDay().getDayOfMonth());
			int[] plan = PLAN[offset + 2];
			int i = 0;
			for (int n = 0; n < plan[0]; n++, i++) {
				LocalDate s = day(period, i, lastDay, 0);
				boolean direct = offset == 0 && n < DIRECT_LIVE_THIS_MONTH;
				// Two months ago, the first live link was lost again this month.
				boolean lost = offset == -2 && n == 0;
				BacklinkDates dates = new BacklinkDates(s, direct ? null : day(period, i, lastDay, 1),
						day(period, i, lastDay, 2), null, lost ? current.firstDay() : null);
				save(lost ? BacklinkStatus.LOST : BacklinkStatus.LIVE, dates, pages, owners);
			}
			for (int n = 0; n < plan[1]; n++, i++) {
				save(BacklinkStatus.APPROVED, new BacklinkDates(day(period, i, lastDay, 0), day(period, i, lastDay, 1), null,
						null, null), pages, owners);
			}
			for (int n = 0; n < plan[2]; n++, i++) {
				save(BacklinkStatus.SUBMITTED, new BacklinkDates(day(period, i, lastDay, 0), null, null, null, null), pages,
						owners);
			}
			for (int n = 0; n < plan[3]; n++, i++) {
				save(BacklinkStatus.REJECTED, new BacklinkDates(day(period, i, lastDay, 0), null, null,
						day(period, i, lastDay, 2), null), pages, owners);
			}
		}
		for (int n = 0; n < PROSPECTS; n++) {
			save(BacklinkStatus.PROSPECTED, BacklinkDates.NONE, pages, owners);
		}
		return counter;
	}

	/** A day in the month for the i-th link, {@code step} days after its submission, never after {@code lastDay}. */
	private static LocalDate day(MarketingPeriod period, int i, int lastDay, int step) {
		int submitted = 1 + (i * 7) % lastDay;
		return period.firstDay().plusDays(Math.min(submitted + step, lastDay) - 1L);
	}

	private void save(BacklinkStatus status, BacklinkDates dates, List<SeoPage> pages, List<User> owners) {
		int n = counter++;
		String domain = DOMAINS[n % DOMAINS.length];
		SeoPage page = pages.isEmpty() ? null : pages.get(n % pages.size());
		Backlink backlink = new Backlink();
		backlink.setCode(codeGenerator.next(CodeGenerator.BACKLINK));
		backlink.setTargetPage(page);
		backlink.setTargetUrl(page == null ? "/services/sap-testing" : page.getUrl());
		backlink.setReferringDomain(domain);
		// Prospects have no link yet; the rest have the page that carries (or would carry) the link.
		backlink.setLinkUrl(status == BacklinkStatus.PROSPECTED ? null : "https://" + domain + "/articles/qa-insights-" + (n + 1));
		backlink.setAnchorText(ANCHORS[n % ANCHORS.length]);
		backlink.setLinkType(TYPES[n % TYPES.length]);
		backlink.setStatus(status);
		backlink.setDates(dates);
		backlink.setDomainAuthority(20 + (n * 13) % 70);
		backlink.setOwner(owners.isEmpty() ? null : owners.get(n % owners.size()));
		backlink.setProvider(BacklinkOrigin.MANUAL);
		backlinkRepository.save(backlink);
	}

}
