package com.teamops.devdata;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.entity.ContentItem;
import com.teamops.marketing.content.entity.ContentStatus;
import com.teamops.marketing.content.entity.ContentType;
import com.teamops.marketing.content.repository.ContentItemRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Blog posts and the content pipeline. Tops each of the last three months up to the seeded Blogs Published actuals
 * (10, 11, then brief section 75 this month: 9 of 12, so 3 remaining), counting posts already there (the lead seed
 * adds a few), adds a case study and a whitepaper that do not count as blogs, marks the oldest post as refreshed, and
 * fills an empty pipeline with ideas, planned posts, posts in progress and a draft. Dates are relative to today and
 * publication dates never in the future. Idempotent: a second run finds every month full and the pipeline there.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevContentSeeder {

	/** Live blog posts per month offset -2, -1, 0. */
	private static final int[] PUBLISHED = { 10, 11, 9 };

	private static final String[] TOPICS = { "SAP S/4HANA regression testing", "Test automation ROI",
			"Banking QA compliance", "Performance testing for SAP Fiori", "Shift-left testing in agile teams",
			"Choosing a software testing partner", "Mobile app testing checklist", "API testing with contract tests",
			"Test data management for SAP", "Accessibility testing basics", "Security testing for web apps",
			"Migrating test suites to Tricentis Tosca" };

	private static final String[] ANGLES = { "a practical guide", "lessons from 2026 projects", "what to measure",
			"common mistakes", "a checklist for QA leads" };

	private final ContentItemRepository contentRepository;

	private final UserRepository userRepository;

	private final EntityManager entityManager;

	private final BusinessCalendar calendar;

	/** @return the number of content items created (0 when everything is already there) */
	@Transactional
	public int seed() {
		LocalDate today = calendar.today();
		MarketingPeriod current = MarketingPeriod.of(today);
		User author = userRepository.findByEmailIgnoreCase("kavya.suresh@teamops.local").orElse(null);
		User owner = userRepository.findByEmailIgnoreCase("priya.menon@teamops.local").orElse(null);
		// Posts are planned for the month they came out in, when nobody planned them.
		entityManager.createQuery("update ContentItem c set c.plannedDate = c.publicationDate "
				+ "where c.plannedDate is null and c.publicationDate is not null and c.contentType = :blog")
			.setParameter("blog", ContentType.BLOG)
			.executeUpdate();
		// Titles (and so URLs) are numbered on from what is already there, so later runs never repeat one.
		int base = (int) contentRepository.count();
		int created = 0;
		for (int offset = -2; offset <= 0; offset++) {
			MarketingPeriod period = current.plusMonths(offset);
			int lastDay = offset == 0 ? today.getDayOfMonth() : period.lastDay().getDayOfMonth();
			long existing = livePosts(period);
			for (long n = existing; n < PUBLISHED[offset + 2]; n++, created++) {
				LocalDate published = period.firstDay().plusDays((n * 3) % lastDay);
				int age = -offset;
				ContentItem post = item(title(base + created), ContentType.BLOG, ContentStatus.PUBLISHED,
						author, owner);
				post.setPlannedDate(published);
				post.setPublicationDate(published);
				post.setOrganicTraffic(120 + age * 340 + (int) (n * 37));
				post.setCtaClicks(4 + age * 9 + (int) (n % 5));
				contentRepository.save(post);
			}
		}
		if (!hasPipeline()) {
			created += seedPipeline(current, today, author, owner, base + created);
		}
		return created;
	}

	private int seedPipeline(MarketingPeriod current, LocalDate today, User author, User owner, int start) {
		MarketingPeriod next = current.next();
		int created = 0;
		// Planned for this month but not published yet: with the 9 published, 12 blogs are planned this month.
		for (ContentStatus status : List.of(ContentStatus.IN_PROGRESS, ContentStatus.IN_PROGRESS, ContentStatus.DRAFT)) {
			ContentItem post = item(title(start + created), ContentType.BLOG, status, author, owner);
			post.setPlannedDate(current.lastDay());
			contentRepository.save(post);
			created++;
		}
		for (int i = 0; i < 4; i++, created++) {
			ContentItem post = item(title(start + created), ContentType.BLOG, ContentStatus.PLANNED, author, owner);
			post.setPlannedDate(next.firstDay().plusDays(i * 7L));
			contentRepository.save(post);
		}
		for (int i = 0; i < 3; i++, created++) {
			contentRepository.save(item(title(start + created), ContentType.BLOG, ContentStatus.IDEA, null, owner));
		}
		// Other content is published too, but only blogs count towards the blog target.
		ContentItem caseStudy = item("Case study: SAP S/4HANA migration testing for a retail group", ContentType.CASE_STUDY,
				ContentStatus.PUBLISHED, author, owner);
		caseStudy.setPublicationDate(current.firstDay());
		caseStudy.setOrganicTraffic(260);
		caseStudy.setCtaClicks(18);
		contentRepository.save(caseStudy);
		ContentItem whitepaper = item("Whitepaper: the state of test automation in Indian banking", ContentType.WHITEPAPER,
				ContentStatus.PUBLISHED, author, owner);
		whitepaper.setPublicationDate(current.previous().firstDay().plusDays(9));
		whitepaper.setOrganicTraffic(540);
		whitepaper.setCtaClicks(41);
		contentRepository.save(whitepaper);
		created += 2;
		// The oldest post was refreshed this month: still published (and counted) in its own month.
		contentRepository.findAll()
			.stream()
			.filter(c -> c.getContentType() == ContentType.BLOG && c.getStatus() == ContentStatus.PUBLISHED
					&& c.getPublicationDate() != null)
			.min((a, b) -> a.getPublicationDate().compareTo(b.getPublicationDate()))
			.ifPresent(oldest -> {
				oldest.setStatus(ContentStatus.UPDATED);
				oldest.setRefreshedDate(today.isBefore(oldest.getPublicationDate()) ? oldest.getPublicationDate() : today);
			});
		return created;
	}

	private long livePosts(MarketingPeriod period) {
		return entityManager.createQuery("""
				select count(c) from ContentItem c where c.contentType = :blog and c.status in :live
				  and c.publicationDate between :from and :to
				""", Long.class)
			.setParameter("blog", ContentType.BLOG)
			.setParameter("live", List.of(ContentStatus.PUBLISHED, ContentStatus.UPDATED))
			.setParameter("from", period.firstDay())
			.setParameter("to", period.lastDay())
			.getSingleResult();
	}

	private boolean hasPipeline() {
		return entityManager.createQuery("select count(c) from ContentItem c where c.status in :early", Long.class)
			.setParameter("early", List.of(ContentStatus.IDEA, ContentStatus.PLANNED, ContentStatus.IN_PROGRESS))
			.getSingleResult() > 0;
	}

	private static String title(int n) {
		return TOPICS[n % TOPICS.length] + ": " + ANGLES[(n / TOPICS.length) % ANGLES.length]
				+ (n >= TOPICS.length * ANGLES.length ? " (" + n + ")" : "");
	}

	private static ContentItem item(String title, ContentType type, ContentStatus status, User author, User owner) {
		ContentItem item = new ContentItem();
		item.setTitle(title);
		item.setContentType(type);
		item.setStatus(status);
		item.setAuthor(author);
		item.setOwner(owner);
		item.setUrl("https://www.example.com/" + (type == ContentType.BLOG ? "blog" : "resources") + "/"
				+ title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""));
		return item;
	}

}
