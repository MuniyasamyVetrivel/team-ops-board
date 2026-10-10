package com.teamops.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageResponse;
import com.teamops.knowledge.dto.KnowledgeDtos;
import com.teamops.knowledge.service.KnowledgeService;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.email.service.EmailCampaignService;
import com.teamops.marketing.lead.service.LeadService;
import com.teamops.marketing.paid.service.PaidCampaignService;
import com.teamops.marketing.seo.service.SeoService;
import com.teamops.project.service.ProjectService;
import com.teamops.search.dto.SearchDtos.Group;
import com.teamops.search.dto.SearchDtos.GroupResult;
import com.teamops.search.dto.SearchDtos.Hit;
import com.teamops.search.dto.SearchDtos.ResultType;
import com.teamops.search.dto.SearchDtos.SearchResults;
import com.teamops.task.dto.TaskSearchCriteria;
import com.teamops.task.dto.TaskView;
import com.teamops.task.service.TaskService;
import com.teamops.team.service.TeamService;
import com.teamops.ticket.dto.TicketRequests;
import com.teamops.ticket.dto.TicketView;
import com.teamops.ticket.service.TicketService;
import com.teamops.user.dto.UserSearchCriteria;
import com.teamops.user.entity.UserStatus;

import lombok.RequiredArgsConstructor;

/**
 * Global search (brief section 66). Each group runs through its module's own list service, so the results obey
 * exactly the same rules as the module's list page: the module's permission decides whether the group is searched at
 * all, and the service's scope (department, own work, published articles) decides which rows match. Digital Marketing
 * groups also need MARKETING_VIEW, like the whole {@code /api/marketing} API.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalSearchService {

	public static final int MIN_QUERY_LENGTH = 2;

	public static final int MAX_QUERY_LENGTH = 100;

	public static final int DEFAULT_LIMIT = 5;

	public static final int MAX_LIMIT = 20;

	private static final Sort NEWEST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

	private final TaskService taskService;

	private final TicketService ticketService;

	private final ProjectService projectService;

	private final TeamService teamService;

	private final KnowledgeService knowledgeService;

	private final SeoService seoService;

	private final EmailCampaignService emailCampaignService;

	private final PaidCampaignService paidCampaignService;

	private final LeadService leadService;

	public SearchResults search(String rawQuery, Set<Group> only, int limit, AuthenticatedUser viewer) {
		String query = rawQuery == null ? "" : rawQuery.strip();
		if (query.length() > MAX_QUERY_LENGTH) {
			query = query.substring(0, MAX_QUERY_LENGTH);
		}
		if (query.length() < MIN_QUERY_LENGTH) {
			return new SearchResults(query, List.of());
		}
		int size = Math.clamp(limit, 1, MAX_LIMIT);
		Pageable newest = PageRequest.of(0, size, NEWEST);
		String q = query;
		List<GroupResult> groups = new ArrayList<>();
		for (Group group : Group.values()) {
			if ((only == null || only.isEmpty() || only.contains(group)) && maySearch(group, viewer)) {
				GroupResult result = run(group, q, size, newest, viewer);
				if (result.total() > 0) {
					groups.add(result);
				}
			}
		}
		return new SearchResults(query, groups);
	}

	/** The permissions each group needs (the module's list permission; marketing also needs MARKETING_VIEW). */
	static boolean maySearch(Group group, AuthenticatedUser viewer) {
		return switch (group) {
			case TASKS -> viewer.hasPermission("TASK_VIEW");
			case TICKETS -> viewer.hasPermission("TICKET_VIEW");
			case PROJECTS -> viewer.hasPermission("PROJECT_VIEW");
			case EMPLOYEES -> viewer.hasPermission("TEAM_VIEW");
			case KNOWLEDGE_BASE -> viewer.hasPermission("KB_VIEW");
			case MARKETING_PAGES, KEYWORDS -> marketing(viewer, "SEO_VIEW");
			case CAMPAIGNS -> marketing(viewer, "CAMPAIGN_VIEW");
			case LEADS -> marketing(viewer, "LEAD_VIEW");
		};
	}

	private static boolean marketing(AuthenticatedUser viewer, String permission) {
		return viewer.hasPermission("MARKETING_VIEW") && viewer.hasPermission(permission);
	}

	private GroupResult run(Group group, String q, int size, Pageable newest, AuthenticatedUser viewer) {
		return switch (group) {
			case TASKS -> result(group,
					taskService.search(new TaskSearchCriteria(q, Set.of(), Set.of(), null, null, null, null,
							TaskView.ALL), newest, viewer),
					t -> new Hit(ResultType.TASK, t.id(), t.code(), t.title(),
							join(t.assignee() == null ? "Unassigned" : t.assignee().fullName(),
									t.project() == null ? null : t.project().name()),
							t.status().name(), null));
			case TICKETS -> result(group,
					ticketService.search(new TicketRequests.Search(q, Set.of(), Set.of(), null, null, null,
							TicketView.ALL), newest, viewer),
					t -> new Hit(ResultType.TICKET, t.id(), t.code(), t.subject(),
							join(t.category() == null ? null : t.category().name(),
									t.requester() == null ? null : t.requester().fullName()),
							t.status().name(), null));
			case PROJECTS -> result(group, projectService.search(q, Set.of(), null, newest, viewer),
					p -> new Hit(ResultType.PROJECT, p.id(), p.code(), p.name(),
							join(p.department() == null ? null : p.department().name(),
									p.owner() == null ? null : p.owner().fullName()),
							p.status().name(), null));
			case EMPLOYEES -> result(group,
					teamService.directory(new UserSearchCriteria(q, null, UserStatus.ACTIVE, null),
							PageRequest.of(0, size, Sort.by("firstName", "lastName", "id"))),
					u -> new Hit(ResultType.EMPLOYEE, u.id(), null, u.fullName(),
							join(u.jobTitle(), u.department() == null ? null : u.department().name()),
							u.status().name(), null));
			case KNOWLEDGE_BASE -> result(group,
					knowledgeService.search(new KnowledgeDtos.Search(q, null, Set.of(), null), newest, viewer),
					a -> new Hit(ResultType.ARTICLE, a.id(), null, a.title(),
							a.category() == null ? null : a.category().name(), a.status().name(), a.slug()));
			case MARKETING_PAGES -> result(group,
					seoService.searchPages(q, Set.of(), Set.of(), null, null, currentMonth(), newest),
					p -> new Hit(ResultType.MARKETING_PAGE, p.id(), null, p.title(), p.url(), p.status().name(),
							null));
			case KEYWORDS -> result(group,
					seoService.searchKeywords(q, null, null, Set.of(), null, currentMonth(), newest),
					k -> new Hit(ResultType.KEYWORD, k.id(), null, k.keyword(),
							k.page() == null ? null : k.page().title(), k.status().name(), null));
			case CAMPAIGNS -> campaigns(q, newest);
			case LEADS -> result(group,
					leadService.search(new LeadService.LeadFilter(q, Set.of(), Set.of(), null, null, null, null, null),
							newest, viewer),
					l -> new Hit(ResultType.LEAD, l.id(), l.code(), l.name(),
							join(l.company(), label(l.source().name())), l.status().name(), null));
		};
	}

	/** Email and paid campaigns in one group, newest first within each kind. */
	private GroupResult campaigns(String q, Pageable newest) {
		var email = emailCampaignService.search(q, Set.of(), Set.of(), null, null, newest);
		var paid = paidCampaignService.search(q, Set.of(), Set.of(), null, null, newest);
		List<Hit> emailHits = email.content()
			.stream()
			.map(c -> new Hit(ResultType.EMAIL_CAMPAIGN, c.id(), null, c.name(),
					join("Email", label(c.campaignType().name())), c.status().name(), null))
			.toList();
		List<Hit> paidHits = paid.content()
			.stream()
			.map(c -> new Hit(ResultType.PAID_CAMPAIGN, c.id(), null, c.name(),
					join("Paid", label(c.platform().name())), c.status().name(), null))
			.toList();
		// Alternate the two kinds so neither hides the other.
		List<Hit> hits = new ArrayList<>();
		for (int i = 0; hits.size() < newest.getPageSize() && (i < emailHits.size() || i < paidHits.size()); i++) {
			if (i < emailHits.size()) {
				hits.add(emailHits.get(i));
			}
			if (i < paidHits.size() && hits.size() < newest.getPageSize()) {
				hits.add(paidHits.get(i));
			}
		}
		return new GroupResult(Group.CAMPAIGNS, Group.CAMPAIGNS.label(), email.totalElements() + paid.totalElements(),
				hits);
	}

	private MarketingPeriod currentMonth() {
		return seoService.period(null, null);
	}

	private static <T> GroupResult result(Group group, PageResponse<T> page, Function<T, Hit> toHit) {
		return new GroupResult(group, group.label(), page.totalElements(), page.content().stream().map(toHit).toList());
	}

	private static String join(String first, String second) {
		if (first == null || first.isBlank()) {
			return second;
		}
		return second == null || second.isBlank() ? first : first + " · " + second;
	}

	/** {@code "LINKEDIN_ADS"} → {@code "Linkedin ads"}, for subtitles. */
	private static String label(String code) {
		String words = code.replace('_', ' ').toLowerCase(Locale.ROOT);
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

}
