package com.teamops.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

import com.teamops.bench.dto.BenchDtos.ClientDto;
import com.teamops.bench.entity.ClientStatus;
import com.teamops.bench.service.ClientService;
import com.teamops.bench.service.ConsultantService;
import com.teamops.bench.service.EngagementService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageResponse;
import com.teamops.knowledge.service.KnowledgeService;
import com.teamops.marketing.email.service.EmailCampaignService;
import com.teamops.marketing.lead.service.LeadService;
import com.teamops.marketing.paid.service.PaidCampaignService;
import com.teamops.marketing.seo.service.SeoService;
import com.teamops.module.dto.ModuleDtos.Tool;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.project.service.ProjectService;
import com.teamops.search.dto.SearchDtos.Group;
import com.teamops.search.dto.SearchDtos.ResultType;
import com.teamops.search.dto.SearchDtos.SearchResults;
import com.teamops.support.SliceAuth;
import com.teamops.task.service.TaskService;
import com.teamops.team.service.TeamService;
import com.teamops.ticket.service.TicketService;
import com.teamops.vendor.dto.VendorDtos.VendorDto;
import com.teamops.vendor.entity.VendorCategory;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.service.VendorContractService;
import com.teamops.vendor.service.VendorService;

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

	@Mock
	private ModuleAccessService moduleAccess;

	@Mock
	private ConsultantService consultantService;

	@Mock
	private ClientService clientService;

	@Mock
	private EngagementService engagementService;

	@Mock
	private VendorService vendorService;

	@Mock
	private VendorContractService contractService;

	private GlobalSearchService service;

	@BeforeEach
	void setUp() {
		service = new GlobalSearchService(taskService, ticketService, projectService, teamService, knowledgeService,
				seoService, emailCampaignService, paidCampaignService, leadService, moduleAccess, consultantService,
				clientService, engagementService, vendorService, contractService);
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

	@Test
	void benchGroupsNeedBenchViewAndTheModuleSwitchedOnForTheViewer() {
		AuthenticatedUser benchReader = user(Set.of("BENCH_VIEW"));
		assertThat(GlobalSearchService.maySearch(Group.CONSULTANTS, benchReader)).isTrue();
		assertThat(GlobalSearchService.maySearch(Group.ENGAGEMENTS, SliceAuth.EMPLOYEE)).isFalse();

		// Holding the permission is not enough while the module is off for the viewer's departments.
		when(moduleAccess.toolsFor(benchReader)).thenReturn(List.of());
		assertThat(service.search("acme", null, 5, benchReader).groups()).isEmpty();
		verifyNoInteractions(consultantService, clientService, engagementService);

		when(moduleAccess.toolsFor(benchReader))
			.thenReturn(List.of(new Tool("bench", "Bench Management", "Users", "", false)));
		when(consultantService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
		when(engagementService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
		when(clientService.search(any(), any())).thenReturn(new PageResponse<>(List.of(new ClientDto(4L,
				"Acme", "Sydney", ClientStatus.ACTIVE, null, 2, 0, null, null)), 0, 5, 1, 1));

		SearchResults results = service.search("acme", null, 5, benchReader);

		assertThat(results.groups()).singleElement().satisfies(group -> {
			assertThat(group.group()).isEqualTo(Group.CLIENTS);
			assertThat(group.hits()).singleElement().satisfies(hit -> {
				assertThat(hit.type()).isEqualTo(ResultType.CLIENT);
				assertThat(hit.title()).isEqualTo("Acme");
				assertThat(hit.subtitle()).isEqualTo("Sydney");
			});
		});
		verify(moduleAccess, times(2)).toolsFor(benchReader);
	}

	@Test
	void vendorGroupsNeedVendorViewAndTheVendorsModuleSwitchedOnForTheViewer() {
		AuthenticatedUser vendorReader = user(Set.of("VENDOR_VIEW", "BENCH_VIEW"));
		assertThat(GlobalSearchService.maySearch(Group.VENDORS, vendorReader)).isTrue();
		assertThat(GlobalSearchService.maySearch(Group.VENDOR_CONTRACTS, SliceAuth.EMPLOYEE)).isFalse();

		// Bench is switched on for this viewer, Vendors is not: vendor groups are skipped, bench groups searched.
		when(moduleAccess.toolsFor(vendorReader))
			.thenReturn(List.of(new Tool("bench", "Bench Management", "Users", "", false)));
		when(consultantService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
		when(clientService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
		when(engagementService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
		assertThat(service.search("acme", null, 5, vendorReader).groups()).isEmpty();
		verifyNoInteractions(vendorService, contractService);

		when(moduleAccess.toolsFor(vendorReader))
			.thenReturn(List.of(new Tool("vendors", "Vendors", "Store", "", false)));
		when(vendorService.search(any(), any())).thenReturn(new PageResponse<>(List.of(new VendorDto(9L, "Acme Software",
				null, VendorCategory.SOFTWARE, "Australia", null, null, null, VendorStatus.ACTIVE, 4, null, List.of(), 1,
				null, 0, null, null)), 0, 5, 1, 1));
		when(contractService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));

		SearchResults results = service.search("acme", null, 5, vendorReader);

		assertThat(results.groups()).singleElement().satisfies(group -> {
			assertThat(group.group()).isEqualTo(Group.VENDORS);
			assertThat(group.hits()).singleElement().satisfies(hit -> {
				assertThat(hit.type()).isEqualTo(ResultType.VENDOR);
				assertThat(hit.subtitle()).isEqualTo("Software · Australia");
				assertThat(hit.status()).isEqualTo("ACTIVE");
			});
		});
		// The module list is read once per search, however many tool groups there are.
		verify(moduleAccess, times(2)).toolsFor(vendorReader);
	}

	private static AuthenticatedUser user(Set<String> permissions) {
		return new AuthenticatedUser(50L, "x@teamops.local", "X", 1L, Set.of("EMPLOYEE"), permissions);
	}

}
