package com.teamops.devdata;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.entity.ContentItem;
import com.teamops.marketing.content.entity.ContentStatus;
import com.teamops.marketing.content.entity.ContentType;
import com.teamops.marketing.content.repository.ContentItemRepository;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.marketing.lead.entity.LeadOrigin;
import com.teamops.marketing.lead.entity.LeadStatus;
import com.teamops.marketing.lead.entity.MarketingLead;
import com.teamops.marketing.lead.repository.MarketingLeadRepository;
import com.teamops.marketing.paid.entity.AdPlatform;
import com.teamops.marketing.paid.entity.PaidCampaign;
import com.teamops.marketing.paid.repository.PaidCampaignRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Published blog posts and marketing leads for this month and the two before it. The counts match the seeded Website
 * Leads actuals (185, 210 and 200), and this month follows brief section 44: organic 80, email 45, LinkedIn 35, blog
 * 20, paid 20. Each email, LinkedIn/paid and blog lead names the latest campaign or post live on its date, when there
 * is one. Dates are relative to today and never in the future. Runs only while there are no leads (content only while
 * there is no content).
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevLeadSeeder {

	private static final String[] FIRST = { "Anita", "Vikram", "Meera", "Rahul", "Sneha", "Arjun", "Kavitha", "Naveen",
			"Priyanka", "Sanjay", "Lavanya", "Ganesh", "Shalini", "Karthik", "Deepa", "Rohit" };

	private static final String[] LAST = { "Rao", "Iyer", "Menon", "Sharma", "Nair", "Reddy", "Pillai", "Gupta", "Shah",
			"Krishnan", "Bose", "Desai" };

	private static final String[] COMPANIES = { "Acme Manufacturing", "Bluepeak Retail", "Coralline Pharma",
			"Deltaway Logistics", "Evergreen Foods", "Fintrail Bank", "Granite Infra", "Horizon Telecom", "Ironleaf Steel",
			"Juniper Health" };

	private final MarketingLeadRepository leadRepository;

	private final ContentItemRepository contentRepository;

	private final EmailCampaignRepository emailCampaignRepository;

	private final PaidCampaignRepository paidCampaignRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final CodeGenerator codeGenerator;

	private final BusinessCalendar calendar;

	/** @return the number of leads created (0 when leads already exist) */
	@Transactional
	public int seedIfEmpty() {
		if (leadRepository.count() > 0) {
			return 0;
		}
		LocalDate today = calendar.today();
		MarketingPeriod thisMonth = MarketingPeriod.of(today);
		User priya = userRepository.findByEmailIgnoreCase("priya.menon@teamops.local").orElse(null);
		User kavya = userRepository.findByEmailIgnoreCase("kavya.suresh@teamops.local").orElse(null);
		Department dm = departmentRepository.findByCode("DM").orElse(null);
		if (contentRepository.count() == 0) {
			seedContent(thisMonth, today, kavya, priya);
		}

		List<EmailCampaign> emails = emailCampaignRepository.findLinkable(null, Pageable.unpaged());
		List<PaidCampaign> paid = paidCampaignRepository.findLinkable(null, Pageable.unpaged());
		List<ContentItem> posts = contentRepository.findLinkable(null, Pageable.unpaged());

		int created = 0;
		created += month(thisMonth.plusMonths(-2), today, counts(75, 45, 25, 15, 0, 15, 10), 2, emails, paid, posts, priya, dm);
		created += month(thisMonth.previous(), today, counts(85, 50, 30, 20, 10, 10, 5), 1, emails, paid, posts, priya, dm);
		created += month(thisMonth, today, counts(80, 45, 35, 20, 20, 0, 0), 0, emails, paid, posts, priya, dm);
		return created;
	}

	private void seedContent(MarketingPeriod thisMonth, LocalDate today, User author, User owner) {
		MarketingPeriod last = thisMonth.previous();
		MarketingPeriod twoAgo = last.previous();
		post("SAP S/4HANA migration testing checklist", twoAgo.firstDay().plusDays(2), author, owner);
		post("Automating SAP regression testing with Tricentis Tosca", twoAgo.firstDay().plusDays(17), author, owner);
		post("Performance testing for SAP Fiori apps", last.firstDay().plusDays(4), author, owner);
		post("How to plan a test automation ROI study", last.firstDay().plusDays(19), author, owner);
		post("SAP testing services: what to expect in 2027", thisMonth.firstDay(), author, owner);
		ContentItem draft = new ContentItem();
		draft.setTitle("Test data management for SAP landscapes");
		draft.setStatus(ContentStatus.DRAFT);
		draft.setAuthor(author);
		draft.setOwner(owner);
		contentRepository.save(draft);
	}

	private void post(String title, LocalDate published, User author, User owner) {
		ContentItem item = new ContentItem();
		item.setTitle(title);
		item.setUrl("https://www.example.com/blog/" + title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
		item.setContentType(ContentType.BLOG);
		item.setStatus(ContentStatus.PUBLISHED);
		item.setPublicationDate(published);
		item.setAuthor(author);
		item.setOwner(owner);
		contentRepository.save(item);
	}

	/** Leads per source in the order organic, email, LinkedIn, blog, paid, website, referral. */
	private static Map<LeadSource, Integer> counts(int organic, int email, int linkedin, int blog, int paidCampaign,
			int website, int referral) {
		Map<LeadSource, Integer> counts = new EnumMap<>(LeadSource.class);
		counts.put(LeadSource.ORGANIC, organic);
		counts.put(LeadSource.EMAIL, email);
		counts.put(LeadSource.LINKEDIN, linkedin);
		counts.put(LeadSource.BLOG, blog);
		counts.put(LeadSource.PAID_CAMPAIGN, paidCampaign);
		counts.put(LeadSource.WEBSITE, website);
		counts.put(LeadSource.REFERRAL, referral);
		return counts;
	}

	/** Spreads a month's leads over its days up to today; older months have moved further through the funnel. */
	private int month(MarketingPeriod period, LocalDate today, Map<LeadSource, Integer> counts, int age,
			List<EmailCampaign> emails, List<PaidCampaign> paid, List<ContentItem> posts, User owner, Department dm) {
		LocalDate last = period.lastDay().isAfter(today) ? today : period.lastDay();
		int days = last.getDayOfMonth();
		List<MarketingLead> leads = new ArrayList<>();
		int n = 0;
		for (Map.Entry<LeadSource, Integer> entry : counts.entrySet()) {
			for (int i = 0; i < entry.getValue(); i++, n++) {
				// One day apart, each source starting on a different day: an even spread for any number of days.
				LocalDate date = period.firstDay().plusDays((i + entry.getKey().ordinal() * 3L) % days);
				leads.add(lead(entry.getKey(), date, n + period.month() * 31, statusFor(age, n), emails, paid, posts,
						owner, dm));
			}
		}
		leadRepository.saveAll(leads);
		return leads.size();
	}

	private MarketingLead lead(LeadSource source, LocalDate date, int seed, LeadStatus status, List<EmailCampaign> emails,
			List<PaidCampaign> paid, List<ContentItem> posts, User owner, Department dm) {
		String first = FIRST[seed % FIRST.length];
		String last = LAST[(seed / FIRST.length + seed) % LAST.length];
		String company = COMPANIES[(seed * 3) % COMPANIES.length];
		MarketingLead lead = new MarketingLead();
		lead.setCode(codeGenerator.next(CodeGenerator.LEAD));
		lead.setName(first + " " + last);
		lead.setCompany(company);
		lead.setEmail((first + "." + last + seed).toLowerCase(Locale.ROOT) + "@"
				+ company.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", "") + ".example");
		lead.setSource(source);
		lead.setLeadDate(date);
		lead.setStatus(status);
		lead.setOwner(owner);
		lead.setDepartment(dm);
		lead.setProvider(LeadOrigin.MANUAL);
		lead.setCreatedBy(owner);
		switch (source) {
			case EMAIL -> lead.setEmailCampaign(emails.stream()
				.filter(c -> !c.getCampaignDate().isAfter(date))
				.max(Comparator.comparing(EmailCampaign::getCampaignDate))
				.orElse(null));
			case LINKEDIN, PAID_CAMPAIGN -> lead.setPaidCampaign(paid.stream()
				.filter(c -> c.getPlatform() == AdPlatform.LINKEDIN || source == LeadSource.PAID_CAMPAIGN)
				.filter(c -> !c.getStartDate().isAfter(date) && (c.getEndDate() == null || !c.getEndDate().isBefore(date)))
				.max(Comparator.comparing(PaidCampaign::getStartDate))
				.orElse(null));
			case BLOG -> lead.setContentItem(posts.stream()
				.filter(c -> c.getPublicationDate() != null && !c.getPublicationDate().isAfter(date))
				.max(Comparator.comparing(ContentItem::getPublicationDate))
				.orElse(null));
			default -> {
				// Organic, website and referral leads name no campaign.
			}
		}
		return lead;
	}

	private static LeadStatus statusFor(int age, int n) {
		int bucket = n % 10;
		return switch (age) {
			case 2 -> bucket < 2 ? LeadStatus.CONVERTED : bucket < 4 ? LeadStatus.LOST : bucket < 7 ? LeadStatus.QUALIFIED
					: bucket < 9 ? LeadStatus.CONTACTED : LeadStatus.NEW;
			case 1 -> bucket < 1 ? LeadStatus.CONVERTED : bucket < 2 ? LeadStatus.LOST : bucket < 5 ? LeadStatus.QUALIFIED
					: bucket < 8 ? LeadStatus.CONTACTED : LeadStatus.NEW;
			default -> bucket < 2 ? LeadStatus.CONTACTED : bucket < 3 ? LeadStatus.QUALIFIED : LeadStatus.NEW;
		};
	}

}
