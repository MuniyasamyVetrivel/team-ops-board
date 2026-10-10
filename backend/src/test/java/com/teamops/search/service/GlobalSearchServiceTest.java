package com.teamops.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageResponse;
import com.teamops.knowledge.service.KnowledgeService;
import com.teamops.marketing.email.service.EmailCampaignService;
import com.teamops.marketing.lead.service.LeadService;
import com.teamops.marketing.paid.service.PaidCampaignService;
import com.teamops.marketing.seo.service.SeoService;
import com.teamops.project.service.ProjectService;
import com.teamops.search.dto.SearchDtos.Group;
import com.teamops.search.dto.SearchDtos.SearchResults;
import com.teamops.support.SliceAuth;
import com.teamops.task.service.TaskService;
import com.teamops.team.service.TeamService;
import com.teamops.ticket.service.TicketService;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

	@Mock
	private TaskService taskService;

	@Mock
	private TicketService ticketService;

	@Mock
	private ProjectService projectService;

	@Mock
	private TeamService teamService;

	@Mock
	private KnowledgeService knowledgeService;

	@Mock
	private SeoService seoService;

	@Mock
	private EmailCampaignService emailCampaignService;

	@Mock
	private PaidCampaignService paidCampaignService;

	@Mock
	private LeadService leadService;

	private GlobalSearchService service;

	@BeforeEach
	void setUp() {
		service = new GlobalSearchService(taskService, ticketService, projectService, teamService, knowledgeService,
				seoService, emailCampaignService, paidCampaignService, leadService);
	}

	@Test
	void eachGroupNeedsItsModulesPermission() {
		AuthenticatedUser employee = SliceAuth.EMPLOYEE;
		assertThat(Arrays.stream(Group.values()).filter(g -> GlobalSearchService.maySearch(g, employee)))
			.containsExactly(Group.TASKS, Group.TICKETS, Group.PROJECTS, Group.EMPLOYEES, Group.KNOWLEDGE_BASE);
		assertThat(Arrays.stream(Group.values()).filter(g -> GlobalSearchService.maySearch(g, SliceAuth.SUPER_ADMIN)))
			.containsExactly(Group.values());
		assertThat(Arrays.stream(Group.values()).filter(g -> GlobalSearchService.maySearch(g, SliceAuth.NOBODY)))
			.isEmpty();
	}

	@Test
	void marketingGroupsAlsoNeedMarketingView() {
		AuthenticatedUser leadsWithoutModule = user(Set.of("LEAD_VIEW"));
		AuthenticatedUser leadsReader = user(Set.of("MARKETING_VIEW", "LEAD_VIEW"));
		assertThat(GlobalSearchService.maySearch(Group.LEADS, leadsWithoutModule)).isFalse();
		assertThat(GlobalSearchService.maySearch(Group.LEADS, leadsReader)).isTrue();
		assertThat(GlobalSearchService.maySearch(Group.KEYWORDS, leadsReader)).isFalse();
		assertThat(GlobalSearchService.maySearch(Group.CAMPAIGNS, leadsReader)).isFalse();
	}

	@Test
	void shortQueriesSearchNothing() {
		SearchResults results = service.search(" a ", null, 5, SliceAuth.SUPER_ADMIN);
		assertThat(results.groups()).isEmpty();
		verifyNoInteractions(taskService, leadService, teamService);
	}

	@Test
	void onlyPermittedAndRequestedGroupsAreSearched() {
		when(taskService.search(any(), any(), eq(SliceAuth.EMPLOYEE))).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));

		SearchResults results = service.search("invoice", Set.of(Group.TASKS, Group.LEADS), 5, SliceAuth.EMPLOYEE);

		assertThat(results.query()).isEqualTo("invoice");
		assertThat(results.groups()).isEmpty();
		verifyNoInteractions(leadService, ticketService, projectService, teamService, knowledgeService, seoService);
	}

	@Test
	void campaignsMixBothKinds() {
		when(emailCampaignService.search(eq("q4"), anySet(), anySet(), any(), any(), any()))
			.thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
		when(paidCampaignService.search(eq("q4"), anySet(), anySet(), any(), any(), any()))
			.thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));

		SearchResults results = service.search("q4", Set.of(Group.CAMPAIGNS), 5, SliceAuth.SUPER_ADMIN);

		assertThat(results.groups()).isEmpty();
	}

	private static AuthenticatedUser user(Set<String> permissions) {
		return new AuthenticatedUser(50L, "x@teamops.local", "X", 1L, Set.of("EMPLOYEE"), permissions);
	}

}
