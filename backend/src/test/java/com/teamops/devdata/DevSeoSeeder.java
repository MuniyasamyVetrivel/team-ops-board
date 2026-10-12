package com.teamops.devdata;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.RankingChange;
import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordRanking;
import com.teamops.marketing.seo.entity.PageType;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.seo.repository.KeywordRankingRepository;
import com.teamops.marketing.seo.repository.SeoKeywordRepository;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * SEO pages and keywords with three months of ranking history ending in the current business month (brief section
 * 72: SAP Testing Services 18 → 12 → 7, S4HANA Testing 25 → 15 → 9). Runs only while there are no SEO pages.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevSeoSeeder {

	/** {@code null} in a history = Not Ranked that month; a keyword without a history was never recorded. */
	private static final Integer NR = null;

	private static final List<SeedPage> PAGES = List.of(
			new SeedPage("/services/sap-testing", "SAP Testing Services", PageType.SERVICE, "SAP Testing Services",
					"arun.kumar@teamops.local", List.of(
							new SeedKeyword("SAP Testing Services", Device.DESKTOP, 5, 1900, 48, 18, 12, 7),
							new SeedKeyword("SAP S4HANA Testing", Device.DESKTOP, 5, 880, 41, 25, 15, 9),
							new SeedKeyword("SAP Testing Company", Device.DESKTOP, 10, 590, 35, 34, 28, 31),
							new SeedKeyword("SAP Application Testing", Device.DESKTOP, 10, 320, 30, NR, 64, 42))),
			new SeedPage("/services/software-testing", "Software Testing Services", PageType.SERVICE,
					"software testing services", "arun.kumar@teamops.local", List.of(
							new SeedKeyword("software testing services", Device.DESKTOP, 10, 6600, 72, 14, 12, 12),
							new SeedKeyword("software testing company", Device.DESKTOP, 10, 4400, 66, 9, 8, 6),
							new SeedKeyword("software testing services India", Device.DESKTOP, 5, 720, 44, 21, 17, 11))),
			new SeedPage("/industries/banking-qa", "Banking QA & Testing", PageType.INDUSTRY,
					"banking software testing", "priya.menon@teamops.local", List.of(
							new SeedKeyword("banking software testing", Device.DESKTOP, 10, 480, 38, 45, 38, 52),
							new SeedKeyword("bank application testing", Device.DESKTOP, 20, 260, 29, NR, NR, 88))),
			new SeedPage("/locations/chennai", "Software Testing Company in Chennai", PageType.LOCATION,
					"software testing company chennai", "arun.kumar@teamops.local", List.of(
							new SeedKeyword("software testing company chennai", Device.DESKTOP, 3, 390, 25, 5, 4, 3),
							new SeedKeyword("qa company in chennai", Device.MOBILE, 5, 210, 22, 11, 9, 10))),
			new SeedPage("/blog/test-automation-roi", "Test Automation ROI", PageType.BLOG, "test automation roi",
					"kavya.suresh@teamops.local", List.of(
							new SeedKeyword("test automation roi", Device.DESKTOP, 10, 170, 18, 27, 19, NR),
							new SeedKeyword("automation testing benefits", Device.DESKTOP, 20, 1300, 33, NR, NR, NR),
							new SeedKeyword("test automation calculator", Device.DESKTOP, 10, 90, 15))),
			new SeedPage("/landing/free-qa-audit", "Free QA Audit", PageType.LANDING_PAGE, null,
					"priya.menon@teamops.local", List.of()));

	private final SeoPageRepository pageRepository;

	private final SeoKeywordRepository keywordRepository;

	private final KeywordRankingRepository rankingRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final BusinessCalendar calendar;

	/** @return the number of keywords created (0 when SEO data already exists) */
	@Transactional
	public int seedIfEmpty() {
		if (pageRepository.count() > 0) {
			return 0;
		}
		Department marketing = departmentRepository.findByCode("DM").orElseThrow();
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		List<MarketingPeriod> months = List.of(current.previous().previous(), current.previous(), current);
		int keywords = 0;
		for (SeedPage seed : PAGES) {
			User owner = userRepository.findByEmailIgnoreCase(seed.ownerEmail()).orElse(null);
			SeoPage page = new SeoPage();
			page.setUrl(seed.url());
			page.setTitle(seed.title());
			page.setPageType(seed.type());
			page.setPrimaryKeyword(seed.primaryKeyword());
			page.setDepartment(marketing);
			page.setOwner(owner);
			pageRepository.save(page);
			for (SeedKeyword k : seed.keywords()) {
				SeoKeyword keyword = new SeoKeyword();
				keyword.setPage(page);
				keyword.setKeyword(k.keyword());
				keyword.setLocation("India");
				keyword.setDevice(k.device());
				keyword.setTargetPosition(k.target());
				keyword.setSearchVolume(k.volume());
				keyword.setKeywordDifficulty(k.difficulty());
				keyword.setOwner(owner);
				keywordRepository.save(keyword);
				recordHistory(keyword, page, owner, months, k.history());
				keywords++;
			}
		}
		keywordRepository.flush();
		keywordRepository.findAll().forEach(keyword -> keywordRepository.refreshRankingCache(keyword.getId()));
		return keywords;
	}

	private void recordHistory(SeoKeyword keyword, SeoPage page, User recordedBy, List<MarketingPeriod> months,
			Integer[] history) {
		for (int i = 0; i < history.length; i++) {
			boolean previousRecorded = i > 0;
			Integer previous = previousRecorded ? history[i - 1] : null;
			KeywordRanking ranking = new KeywordRanking();
			ranking.setKeyword(keyword);
			ranking.setPage(page);
			ranking.setMonth(months.get(i).month());
			ranking.setYear(months.get(i).year());
			ranking.setPosition(history[i]);
			ranking.setPreviousPosition(previous);
			ranking.setRankingChange(RankingChange.of(previousRecorded, previous, history[i]).value());
			ranking.setSearchVolume(keyword.getSearchVolume());
			ranking.setRecordedBy(recordedBy);
			rankingRepository.save(ranking);
		}
	}

	record SeedPage(String url, String title, PageType type, String primaryKeyword, String ownerEmail,
			List<SeedKeyword> keywords) {
	}

	/** {@code history} holds the positions for the month before last, last month and this month (or nothing). */
	record SeedKeyword(String keyword, Device device, Integer target, Integer volume, Integer difficulty,
			Integer... history) {
	}

}
