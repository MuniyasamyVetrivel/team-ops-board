package com.teamops.ticket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.JwtTokenService;
import com.teamops.common.security.UserPrincipalService;
import com.teamops.common.web.PageResponse;
import com.teamops.sla.controller.SlaController;
import com.teamops.sla.service.SlaService;
import com.teamops.support.SecuritySliceTest;
import com.teamops.support.SliceAuth;
import com.teamops.ticket.dto.TicketRequests;
import com.teamops.ticket.dto.TicketView;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.service.TicketCollaborationService;
import com.teamops.ticket.service.TicketService;

/** Authorization, request binding and validation for the Phase 7 endpoints. Services are mocked; no database. */
@WebMvcTest(controllers = { TicketController.class, SlaController.class })
@SecuritySliceTest
class TicketApiSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserPrincipalService userPrincipalService;

	@MockitoBean
	private TicketService ticketService;

	@MockitoBean
	private TicketCollaborationService collaborationService;

	@MockitoBean
	private SlaService slaService;

	private String bearer(AuthenticatedUser user) {
		return SliceAuth.bearer(jwtTokenService, userPrincipalService, user);
	}

	@Test
	void ticketsRequireAuthenticationAndTicketView() throws Exception {
		mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/tickets").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/sla/summary").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.NOBODY)))
			.andExpect(status().isForbidden());
		verifyNoInteractions(ticketService, slaService);
	}

	@Test
	void searchBindsFiltersViewsAndWhitelistedSorts() throws Exception {
		when(ticketService.search(any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 25, 0, 0));

		mvc.perform(get("/api/tickets").param("status", "NEW", "WAITING_FOR_REQUESTER")
			.param("priority", "URGENT")
			.param("view", "ASSIGNED_TO_ME")
			.param("sort", "priority,desc")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isOk());

		ArgumentCaptor<TicketRequests.Search> criteria = ArgumentCaptor.forClass(TicketRequests.Search.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(ticketService).search(criteria.capture(), pageable.capture(), eq(SliceAuth.EMPLOYEE));
		org.assertj.core.api.Assertions.assertThat(criteria.getValue().statuses())
			.isEqualTo(Set.of(TicketStatus.NEW, TicketStatus.WAITING_FOR_REQUESTER));
		org.assertj.core.api.Assertions.assertThat(criteria.getValue().priorities())
			.containsExactly(TicketPriority.URGENT);
		org.assertj.core.api.Assertions.assertThat(criteria.getValue().view()).isEqualTo(TicketView.ASSIGNED_TO_ME);
		org.assertj.core.api.Assertions.assertThat(pageable.getValue().getSort().getOrderFor("priorityRank"))
			.extracting(Sort.Order::getDirection)
			.isEqualTo(Sort.Direction.DESC);

		mvc.perform(get("/api/tickets").param("sort", "requesterPassword,asc")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SORT"));
	}

	@Test
	void anyEmployeeCanRaiseATicketButTheBodyIsValidated() throws Exception {
		mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
			.content("{\"subject\":\" \"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verifyNoInteractions(ticketService);

		mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
			.content("{\"subject\":\"Printer jammed\",\"categoryId\":3,\"priority\":\"HIGH\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isCreated());
		verify(ticketService).create(eq(new TicketRequests.CreateTicket("Printer jammed", null, 3L, null,
				TicketPriority.HIGH, null)), eq(SliceAuth.EMPLOYEE), any());
	}

	@Test
	void workingOnTicketsNeedsTicketEditButRequestersMayChangeStatus() throws Exception {
		mvc.perform(put("/api/tickets/5").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"subject\":\"x\",\"categoryId\":1,\"departmentId\":1,\"priority\":\"LOW\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		mvc.perform(put("/api/tickets/5/assignee").contentType(MediaType.APPLICATION_JSON)
			.content("{\"assigneeId\":4}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());
		verifyNoInteractions(ticketService);

		// Status: the service decides (requesters may confirm or reopen a resolved ticket).
		mvc.perform(put("/api/tickets/5/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"CLOSED\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isOk());
		verify(ticketService).changeStatus(eq(5L), eq(TicketStatus.CLOSED), eq(SliceAuth.EMPLOYEE), any());

		mvc.perform(put("/api/tickets/5/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"ARCHIVED\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isBadRequest());
	}

	@Test
	void repliesBindTheInternalFlag() throws Exception {
		mvc.perform(post("/api/tickets/5/comments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"Checked the logs\",\"internal\":true}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isOk());
		verify(collaborationService).addComment(5L, "Checked the logs", true, SliceAuth.SUPER_ADMIN);

		mvc.perform(post("/api/tickets/5/comments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"\"}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isBadRequest());
	}

	@Test
	void slaPoliciesAreReadableButOnlySlaManagersChangeThem() throws Exception {
		String body = "{\"version\":0,\"firstResponseMinutes\":30,\"resolutionMinutes\":120}";
		mvc.perform(get("/api/sla/policies").header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE)))
			.andExpect(status().isOk());
		mvc.perform(put("/api/sla/policies/1").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isForbidden());

		mvc.perform(put("/api/sla/policies/1").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":0,\"firstResponseMinutes\":0,\"resolutionMinutes\":120}")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isBadRequest());
		mvc.perform(put("/api/sla/policies/1").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.SUPER_ADMIN))).andExpect(status().isOk());

		mvc.perform(get("/api/sla/summary").param("days", "90")
			.header(HttpHeaders.AUTHORIZATION, bearer(SliceAuth.EMPLOYEE))).andExpect(status().isOk());
		verify(slaService).summary(90, SliceAuth.EMPLOYEE);
	}

}
