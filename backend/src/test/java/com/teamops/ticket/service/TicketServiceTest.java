package com.teamops.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.storage.FileService;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.service.NotificationService;
import com.teamops.sla.entity.SlaPolicy;
import com.teamops.sla.repository.SlaPolicyRepository;
import com.teamops.sla.service.SlaState;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.ticket.dto.TicketDtos.TicketDetail;
import com.teamops.ticket.dto.TicketRequests;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketCategory;
import com.teamops.ticket.entity.TicketComment;
import com.teamops.ticket.entity.TicketHistory;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.repository.TicketAttachmentRepository;
import com.teamops.ticket.repository.TicketCategoryRepository;
import com.teamops.ticket.repository.TicketCommentRepository;
import com.teamops.ticket.repository.TicketHistoryRepository;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

class TicketServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-08T05:00:00Z");

	/** IT department manager: works on and assigns IT tickets. */
	private static final AuthenticatedUser IT_MANAGER = new AuthenticatedUser(3L, "suresh.babu@teamops.local",
			"Suresh Babu", 1L, Set.of("DEPARTMENT_MANAGER"),
			Set.of("TICKET_VIEW", "TICKET_CREATE", "TICKET_EDIT", "TICKET_ASSIGN"));

	/** Another Web Development employee: neither requester, assignee nor agent. */
	private static final AuthenticatedUser OUTSIDER = new AuthenticatedUser(9L, "manoj@teamops.local", "Manoj",
			5L, Set.of("EMPLOYEE"), SliceAuth.EMPLOYEE_PERMISSIONS);

	private final TicketRepository ticketRepository = mock(TicketRepository.class);

	private final TicketCategoryRepository categoryRepository = mock(TicketCategoryRepository.class);

	private final TicketCommentRepository commentRepository = mock(TicketCommentRepository.class);

	private final TicketHistoryRepository historyRepository = mock(TicketHistoryRepository.class);

	private final SlaPolicyRepository policyRepository = mock(SlaPolicyRepository.class);

	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private final UserRepository userRepository = mock(UserRepository.class);

	private final CodeGenerator codeGenerator = mock(CodeGenerator.class);

	private final AccessScopeService scopes = mock(AccessScopeService.class);

	private final AppSettingsService settings = mock(AppSettingsService.class);

	private final NotificationService notifications = mock(NotificationService.class);

	private final AuditService auditService = mock(AuditService.class);

	private final ClientInfo client = ClientInfo.unknown();

	private final Department it = TestFixtures.department(1L, "IT", "IT");

	private final Department webDev = TestFixtures.department(5L, "WEBDEV", "Web Development");

	private final User karthik = user(4L, webDev);

	private final User vignesh = user(11L, it);

	private TicketService service;

	private TicketCollaborationService collaboration;

	@BeforeEach
	void setUp() {
		BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata");
		service = new TicketService(ticketRepository, categoryRepository, commentRepository,
				mock(TicketAttachmentRepository.class), historyRepository, policyRepository, departmentRepository,
				userRepository, codeGenerator, scopes, settings, notifications, auditService, calendar);
		collaboration = new TicketCollaborationService(service, commentRepository,
				mock(TicketAttachmentRepository.class), userRepository, mock(FileService.class), calendar);
		when(scopes.scopeFor(SliceAuth.EMPLOYEE)).thenReturn(AccessScope.own(4L));
		when(scopes.scopeFor(OUTSIDER)).thenReturn(AccessScope.own(9L));
		when(scopes.scopeFor(IT_MANAGER)).thenReturn(AccessScope.departments(3L, Set.of(1L)));
		when(departmentRepository.findById(1L)).thenReturn(Optional.of(it));
		when(departmentRepository.findById(5L)).thenReturn(Optional.of(webDev));
		when(userRepository.getReferenceById(any())).thenAnswer(inv -> user(inv.getArgument(0), webDev));
		when(userRepository.findWithDepartmentById(4L)).thenReturn(Optional.of(karthik));
		when(userRepository.findWithDepartmentById(11L)).thenReturn(Optional.of(vignesh));
		when(codeGenerator.next(CodeGenerator.TICKET)).thenReturn("TKT-000042");
		when(settings.slaWarningThresholdPct()).thenReturn(75);
		when(policyRepository.findByPriority(TicketPriority.URGENT)).thenReturn(Optional.of(policy(60, 240)));
		when(policyRepository.findByPriority(TicketPriority.MEDIUM)).thenReturn(Optional.of(policy(240, 1440)));
		when(categoryRepository.findById(20L)).thenReturn(Optional.of(category(20L, "IT Support", it)));
		when(categoryRepository.findById(21L)).thenReturn(Optional.of(category(21L, "Other", null)));
		when(ticketRepository.save(any())).thenAnswer(inv -> {
			Ticket ticket = inv.getArgument(0);
			ReflectionTestUtils.setField(ticket, "id", 42L);
			return ticket;
		});
	}

	// --- creation and the SLA snapshot ----------------------------------------------------------------------

	@Test
	void createSnapshotsSlaDueTimesAndRoutesToTheCategoryTeam() {
		TicketDetail detail = service.create(new TicketRequests.CreateTicket("  Laptop will not boot ", null, 20L,
				5L, TicketPriority.URGENT, null), SliceAuth.EMPLOYEE, client);

		assertThat(detail.code()).isEqualTo("TKT-000042");
		assertThat(detail.subject()).isEqualTo("Laptop will not boot");
		assertThat(detail.status()).isEqualTo(TicketStatus.NEW);
		assertThat(detail.department().code()).as("the category's team, not the requester's").isEqualTo("IT");
		assertThat(detail.requester().id()).isEqualTo(4L);
		assertThat(detail.sla().firstResponse().dueAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
		assertThat(detail.sla().resolution().dueAt()).isEqualTo(NOW.plus(Duration.ofHours(4)));
		assertThat(detail.sla().overall()).isEqualTo(SlaState.ON_TRACK);
		verify(auditService).record(eq(AuditAction.TICKET_CREATED), eq(4L), eq("TICKET"), eq(42L), anyMap(),
				eq(client));
		verify(historyRepository).save(argThat((TicketHistory h) -> h.getFieldName().equals("created")));
	}

	@Test
	void defaultsToMediumPriority() {
		TicketDetail detail = service.create(new TicketRequests.CreateTicket("VPN slow", null, 20L, null, null, null),
				SliceAuth.EMPLOYEE, client);

		assertThat(detail.priority()).isEqualTo(TicketPriority.MEDIUM);
		assertThat(detail.sla().resolution().dueAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
	}

	@Test
	void aCategoryWithoutATeamNeedsADepartment() {
		assertError(() -> service.create(new TicketRequests.CreateTicket("Question", null, 21L, null, null, null),
				SliceAuth.EMPLOYEE, client), HttpStatus.BAD_REQUEST, "DEPARTMENT_REQUIRED");

		assertThat(service.create(new TicketRequests.CreateTicket("Question", null, 21L, 5L, null, null),
				SliceAuth.EMPLOYEE, client).department().code()).isEqualTo("WEBDEV");
	}

	@Test
	void requestersCannotAssignButManagersCanAndTheTicketOpens() {
		assertError(() -> service.create(new TicketRequests.CreateTicket("x", null, 20L, null, null, 11L),
				SliceAuth.EMPLOYEE, client), HttpStatus.FORBIDDEN, "CANNOT_ASSIGN");

		TicketDetail assigned = service.create(new TicketRequests.CreateTicket("x", null, 20L, null, null, 11L),
				IT_MANAGER, client);
		assertThat(assigned.assignee().id()).isEqualTo(11L);
		assertThat(assigned.status()).isEqualTo(TicketStatus.OPEN);
		verify(notifications).notify(eq(11L), eq(NotificationType.TICKET_ASSIGNED), eq("TKT-000042 assigned to you"),
				any(), eq("TICKET"), eq(42L), any());
	}

	// --- status changes and the SLA clock -------------------------------------------------------------------

	@Test
	void waitingForTheRequesterPausesTheClockAndResumingAddsThePause() {
		Ticket ticket = ticket(TicketStatus.OPEN);

		TicketService.applyStatus(ticket, TicketStatus.WAITING_FOR_REQUESTER, NOW);
		assertThat(ticket.getSlaPausedAt()).isEqualTo(NOW);

		TicketService.applyStatus(ticket, TicketStatus.IN_PROGRESS, NOW.plus(Duration.ofMinutes(45)));
		assertThat(ticket.getSlaPausedAt()).isNull();
		assertThat(ticket.getSlaPausedMinutes()).isEqualTo(45);
	}

	@Test
	void resolvingStampsResolutionAndFirstResponseAndReopeningClearsTheResolution() {
		Ticket ticket = ticket(TicketStatus.IN_PROGRESS);

		TicketService.applyStatus(ticket, TicketStatus.RESOLVED, NOW);
		assertThat(ticket.getResolvedAt()).isEqualTo(NOW);
		assertThat(ticket.getFirstRespondedAt()).as("resolving is a response").isEqualTo(NOW);
		assertThat(ticket.getSlaPausedAt()).isEqualTo(NOW);

		TicketService.applyStatus(ticket, TicketStatus.OPEN, NOW.plus(Duration.ofMinutes(30)));
		assertThat(ticket.getResolvedAt()).isNull();
		assertThat(ticket.getSlaPausedMinutes()).as("time spent resolved does not count").isEqualTo(30);
		assertThat(ticket.getFirstRespondedAt()).isEqualTo(NOW);
	}

	@Test
	void closingAnUnresolvedTicketAlsoResolvesIt() {
		Ticket ticket = ticket(TicketStatus.OPEN);

		TicketService.applyStatus(ticket, TicketStatus.CLOSED, NOW);

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
		assertThat(ticket.getResolvedAt()).isEqualTo(NOW);
		assertThat(ticket.getClosedAt()).isEqualTo(NOW);
	}

	@Test
	void statusTransitionsFollowTheLifecycle() {
		assertThat(TicketStatus.NEW.canMoveTo(TicketStatus.OPEN)).isTrue();
		assertThat(TicketStatus.RESOLVED.canMoveTo(TicketStatus.OPEN)).as("reopen").isTrue();
		assertThat(TicketStatus.CLOSED.canMoveTo(TicketStatus.IN_PROGRESS)).isFalse();
		assertThat(TicketStatus.OPEN.canMoveTo(TicketStatus.NEW)).isFalse();
		assertThat(TicketStatus.WAITING_FOR_REQUESTER.clockRuns()).isFalse();
		assertThat(TicketStatus.OPEN_STATUSES).doesNotContain(TicketStatus.RESOLVED, TicketStatus.CLOSED);

		existing(TicketStatus.CLOSED);
		assertError(() -> service.changeStatus(42L, TicketStatus.IN_PROGRESS, IT_MANAGER, client),
				HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION");
	}

	@Test
	void requestersMayOnlyConfirmOrReopenAResolvedTicket() {
		Ticket ticket = existing(TicketStatus.OPEN);
		assertError(() -> service.changeStatus(42L, TicketStatus.RESOLVED, SliceAuth.EMPLOYEE, client),
				HttpStatus.FORBIDDEN, "FORBIDDEN");

		ticket.setStatus(TicketStatus.RESOLVED);
		ticket.setResolvedAt(NOW);
		ticket.setAssignee(vignesh);
		TicketDetail closed = service.changeStatus(42L, TicketStatus.CLOSED, SliceAuth.EMPLOYEE, client);

		assertThat(closed.status()).isEqualTo(TicketStatus.CLOSED);
		verify(auditService).record(eq(AuditAction.TICKET_STATUS_CHANGED), eq(4L), eq("TICKET"), eq(42L), anyMap(),
				eq(client));
		verify(notifications).notify(eq(11L), eq(NotificationType.TICKET_UPDATED), eq("TKT-000042 is now Closed"),
				any(), eq("TICKET"), eq(42L), any());
	}

	@Test
	void agentsResolvingNotifyTheRequester() {
		existing(TicketStatus.IN_PROGRESS);

		TicketDetail resolved = service.changeStatus(42L, TicketStatus.RESOLVED, IT_MANAGER, client);

		assertThat(resolved.resolvedAt()).isEqualTo(NOW);
		assertThat(resolved.permissions().allowedStatuses()).contains(TicketStatus.OPEN, TicketStatus.CLOSED);
		verify(notifications).notify(eq(4L), eq(NotificationType.TICKET_UPDATED), eq("TKT-000042 is now Resolved"),
				any(), eq("TICKET"), eq(42L), any());
	}

	@Test
	void onlyManagersReopenClosedTickets() {
		Ticket ticket = existing(TicketStatus.CLOSED);
		AuthenticatedUser agent = new AuthenticatedUser(11L, "vignesh@teamops.local", "Vignesh", 1L,
				Set.of("EMPLOYEE"), Set.of("TICKET_VIEW", "TICKET_EDIT"));
		when(scopes.scopeFor(agent)).thenReturn(AccessScope.own(11L));

		assertThat(new TicketAccess(agent, AccessScope.own(11L)).allowedStatuses(ticket)).isEmpty();
		assertThat(new TicketAccess(IT_MANAGER, AccessScope.departments(3L, Set.of(1L))).allowedStatuses(ticket))
			.containsExactly(TicketStatus.OPEN);
	}

	// --- visibility and assignment --------------------------------------------------------------------------

	@Test
	void ticketsTheActorCannotSeeLookLikeTheyDoNotExist() {
		existing(TicketStatus.OPEN);

		assertError(() -> service.get(42L, OUTSIDER), HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND");
		assertThat(service.get(42L, SliceAuth.EMPLOYEE).code()).as("the requester").isEqualTo("TKT-000042");
		assertThat(service.get(42L, IT_MANAGER).permissions().canWork()).isTrue();
	}

	@Test
	void assigningANewTicketOpensItAndOutsidersOfTheTeamCannotBeAssigned() {
		existing(TicketStatus.NEW);

		assertError(() -> service.assign(42L, 4L, IT_MANAGER, client), HttpStatus.FORBIDDEN, "CANNOT_ASSIGN");

		TicketDetail detail = service.assign(42L, 11L, IT_MANAGER, client);
		assertThat(detail.assignee().id()).isEqualTo(11L);
		assertThat(detail.status()).isEqualTo(TicketStatus.OPEN);
		verify(auditService).record(eq(AuditAction.TICKET_ASSIGNED), eq(3L), eq("TICKET"), eq(42L), anyMap(),
				eq(client));
	}

	@Test
	void agentsMayTakeATicketThemselvesWithoutTicketAssign() {
		Ticket ticket = existing(TicketStatus.NEW);
		AuthenticatedUser agent = new AuthenticatedUser(11L, "vignesh@teamops.local", "Vignesh", 1L,
				Set.of("EMPLOYEE"), Set.of("TICKET_VIEW", "TICKET_EDIT"));
		TicketAccess access = new TicketAccess(agent, AccessScope.own(11L));

		assertThat(access.canView(ticket)).as("agent in the handling department").isTrue();
		assertThat(access.canAssign(ticket, vignesh)).isTrue();
		assertThat(access.canAssign(ticket, user(12L, it))).as("needs TICKET_ASSIGN for others").isFalse();
	}

	// --- replies --------------------------------------------------------------------------------------------

	@Test
	void internalNotesAreHiddenFromTheRequester() {
		Ticket ticket = existing(TicketStatus.OPEN);
		when(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(42L))
			.thenReturn(List.of(comment(ticket, vignesh, "Looks like a disk failure", true),
					comment(ticket, vignesh, "We are on it", false)));

		assertThat(service.get(42L, SliceAuth.EMPLOYEE).comments()).extracting(c -> c.body())
			.containsExactly("We are on it");
		assertThat(service.get(42L, IT_MANAGER).comments()).hasSize(2);
		assertError(() -> collaboration.addComment(42L, "note", true, SliceAuth.EMPLOYEE), HttpStatus.FORBIDDEN,
				"FORBIDDEN");
	}

	@Test
	void theFirstAgentReplyIsTheFirstResponseAndOpensANewTicket() {
		Ticket ticket = existing(TicketStatus.NEW);

		collaboration.addComment(42L, "Can you share the error?", false, IT_MANAGER);

		assertThat(ticket.getFirstRespondedAt()).isEqualTo(NOW);
		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
		verify(notifications).notify(eq(4L), eq(NotificationType.TICKET_REPLY), eq("New reply on TKT-000042"), any(),
				eq("TICKET"), eq(42L), any());
	}

	@Test
	void internalNotesAreNotAFirstResponse() {
		Ticket ticket = existing(TicketStatus.NEW);

		collaboration.addComment(42L, "Check the asset register", true, IT_MANAGER);

		assertThat(ticket.getFirstRespondedAt()).isNull();
		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
		verify(notifications, never()).notify(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void aRequesterReplyWhileWaitingResumesTheClock() {
		Ticket ticket = existing(TicketStatus.WAITING_FOR_REQUESTER);
		ticket.setSlaPausedAt(NOW.minus(Duration.ofMinutes(20)));
		ticket.setAssignee(vignesh);

		collaboration.addComment(42L, "Here is the screenshot", false, SliceAuth.EMPLOYEE);

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
		assertThat(ticket.getSlaPausedAt()).isNull();
		assertThat(ticket.getSlaPausedMinutes()).isEqualTo(20);
		assertThat(ticket.getFirstRespondedAt()).as("a requester reply is not a response").isNull();
		verify(notifications).notify(eq(11L), eq(NotificationType.TICKET_REPLY), any(), any(), eq("TICKET"), eq(42L),
				any());
	}

	@Test
	void closedTicketsTakeNoPublicReplies() {
		existing(TicketStatus.CLOSED);

		assertError(() -> collaboration.addComment(42L, "Hello?", false, SliceAuth.EMPLOYEE), HttpStatus.BAD_REQUEST,
				"TICKET_CLOSED");
	}

	@Test
	void staleUpdatesAreRejected() {
		existing(TicketStatus.OPEN);

		assertError(() -> service.update(42L, new TicketRequests.UpdateTicket(5, "x", null, 20L, 1L,
				TicketPriority.HIGH), IT_MANAGER), HttpStatus.CONFLICT, "STALE_UPDATE");
	}

	@Test
	void statusLabelsReadNaturally() {
		assertThat(TicketService.label(TicketStatus.WAITING_FOR_REQUESTER)).isEqualTo("Waiting for requester");
		assertThat(TicketService.label(TicketStatus.IN_PROGRESS)).isEqualTo("In progress");
	}

	// --- fixtures -------------------------------------------------------------------------------------------

	/** An IT ticket raised by Karthik (employee 4), created one hour ago with URGENT targets. */
	private Ticket existing(TicketStatus status) {
		Ticket ticket = ticket(status);
		when(ticketRepository.findDetailedById(42L)).thenReturn(Optional.of(ticket));
		return ticket;
	}

	private Ticket ticket(TicketStatus status) {
		Ticket ticket = new Ticket();
		ReflectionTestUtils.setField(ticket, "id", 42L);
		ticket.setCode("TKT-000042");
		ticket.setSubject("Laptop will not boot");
		ticket.setDepartment(it);
		ticket.setRequester(karthik);
		ticket.setStatus(status);
		ticket.setPriority(TicketPriority.URGENT);
		Instant start = NOW.minus(Duration.ofHours(1));
		ticket.setSlaStartAt(start);
		ticket.setFirstResponseDueAt(start.plus(Duration.ofHours(1)));
		ticket.setResolutionDueAt(start.plus(Duration.ofHours(4)));
		ticket.setSlaWarningPct(75);
		ticket.setVersion(0);
		return ticket;
	}

	private static SlaPolicy policy(int firstResponse, int resolution) {
		SlaPolicy policy = new SlaPolicy();
		policy.setName("Policy");
		policy.setFirstResponseMinutes(firstResponse);
		policy.setResolutionMinutes(resolution);
		return policy;
	}

	private static TicketCategory category(long id, String name, Department team) {
		TicketCategory category = new TicketCategory();
		ReflectionTestUtils.setField(category, "id", id);
		category.setName(name);
		category.setDefaultDepartment(team);
		return category;
	}

	private static TicketComment comment(Ticket ticket, User author, String body, boolean internal) {
		TicketComment comment = new TicketComment();
		comment.setTicket(ticket);
		comment.setAuthor(author);
		comment.setBody(body);
		comment.setInternal(internal);
		return comment;
	}

	private static User user(long id, Department department) {
		User user = TestFixtures.user(id, "user" + id + "@teamops.local", TestFixtures.role(3L, "EMPLOYEE"));
		user.setDepartment(department);
		return user;
	}

	private static void assertError(ThrowingCallable call, HttpStatus status, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
