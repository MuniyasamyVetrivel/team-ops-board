package com.teamops.sla.service;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.ClientInfo;
import com.teamops.sla.dto.SlaDtos.PriorityRow;
import com.teamops.sla.dto.SlaDtos.Summary;
import com.teamops.sla.dto.SlaDtos.UpdatePolicy;
import com.teamops.sla.entity.SlaPolicy;
import com.teamops.sla.repository.SlaPolicyRepository;
import com.teamops.support.SliceAuth;
import com.teamops.support.TestFixtures;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.ticket.service.TicketService;

class SlaServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

	private static final Instant SINCE = NOW.minus(Duration.ofDays(30));

	private final SlaPolicyRepository policyRepository = mock(SlaPolicyRepository.class);

	private final AuditService auditService = mock(AuditService.class);

	private SlaService service;

	@BeforeEach
	void setUp() {
		service = new SlaService(policyRepository, mock(TicketRepository.class), mock(TicketService.class),
				mock(AppSettingsService.class), auditService,
				new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"));
	}

	@Test
	void summaryMeasuresComplianceOnTicketsCreatedInTheWindow() {
		// URGENT 1h/4h: one met in time, one late; one breached and still open; one old ticket outside the window.
		Ticket metInTime = ticket("TKT-1", TicketPriority.URGENT, NOW.minus(Duration.ofDays(2)), 30, 120, TicketStatus.RESOLVED);
		Ticket late = ticket("TKT-2", TicketPriority.URGENT, NOW.minus(Duration.ofDays(3)), 90, 300, TicketStatus.CLOSED);
		Ticket breachedOpen = ticket("TKT-3", TicketPriority.URGENT, NOW.minus(Duration.ofHours(5)), 20, null,
				TicketStatus.IN_PROGRESS);
		Ticket oldResolved = ticket("TKT-4", TicketPriority.URGENT, NOW.minus(Duration.ofDays(60)), 300, 600,
				TicketStatus.CLOSED);

		Summary summary = SlaService.summarise(List.of(metInTime, late, breachedOpen, oldResolved), policies(), SINCE,
				NOW, 30, 75);

		assertThat(summary.created()).isEqualTo(3);
		assertThat(summary.firstResponseCompliance()).as("2 of 3 first responses in time").isEqualTo(67);
		assertThat(summary.resolutionCompliance()).as("1 of 3 decided resolutions in time").isEqualTo(33);
		assertThat(summary.open()).isEqualTo(1);
		assertThat(summary.breached()).isEqualTo(1);
		assertThat(summary.atRisk()).singleElement().satisfies(item -> assertThat(item.code()).isEqualTo("TKT-3"));
		PriorityRow urgent = summary.priorities().get(0);
		assertThat(urgent.priority()).isEqualTo(TicketPriority.URGENT);
		assertThat(urgent.firstResponseMinutes()).isEqualTo(60);
		assertThat(urgent.created()).isEqualTo(3);
		assertThat(urgent.openBreached()).isEqualTo(1);
	}

	@Test
	void openTicketsAreCountedByStateWhateverTheirAgeAndPausedOnesAreFlagged() {
		Ticket onTrack = ticket("TKT-5", TicketPriority.LOW, NOW.minus(Duration.ofHours(1)), null, null, TicketStatus.NEW);
		Ticket warning = ticket("TKT-6", TicketPriority.URGENT, NOW.minus(Duration.ofMinutes(50)), null, null,
				TicketStatus.OPEN);
		Ticket waiting = ticket("TKT-7", TicketPriority.MEDIUM, NOW.minus(Duration.ofDays(90)), 10, null,
				TicketStatus.WAITING_FOR_REQUESTER);
		waiting.setSlaPausedAt(waiting.getSlaStartAt().plus(Duration.ofMinutes(30)));

		Summary summary = SlaService.summarise(List.of(onTrack, warning, waiting), policies(), SINCE, NOW, 30, 75);

		assertThat(summary.open()).isEqualTo(3);
		assertThat(summary.onTrack()).isEqualTo(2);
		assertThat(summary.warning()).as("50 of 60 first-response minutes used").isEqualTo(1);
		assertThat(summary.paused()).isEqualTo(1);
		assertThat(summary.created()).as("the 90-day-old ticket is outside the window").isEqualTo(2);
		assertThat(summary.resolutionCompliance()).as("nothing decided yet").isNull();
	}

	@Test
	void emptyWindowGivesNullCompliance() {
		Summary summary = SlaService.summarise(List.of(), policies(), SINCE, NOW, 30, 75);

		assertThat(summary.firstResponseCompliance()).isNull();
		assertThat(summary.priorities()).hasSize(4).allSatisfy(row -> assertThat(row.created()).isZero());
	}

	@Test
	void windowMustBeReasonable() {
		assertThatThrownBy(() -> service.summary(0, SliceAuth.SUPER_ADMIN)).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.summary(366, SliceAuth.SUPER_ADMIN)).isInstanceOf(ApiException.class);
	}

	@Test
	void policyUpdatesAreValidatedVersionedAndAudited() {
		SlaPolicy policy = policy(TicketPriority.HIGH, 120, 480);
		policy.setVersion(1);
		when(policyRepository.findById(2L)).thenReturn(Optional.of(policy));

		assertCode(() -> service.updatePolicy(2L, new UpdatePolicy(0, 60, 240), SliceAuth.SUPER_ADMIN, null),
				HttpStatus.CONFLICT, "STALE_UPDATE");
		assertCode(() -> service.updatePolicy(2L, new UpdatePolicy(1, 300, 240), SliceAuth.SUPER_ADMIN, null),
				HttpStatus.BAD_REQUEST, "INVALID_SLA");

		service.updatePolicy(2L, new UpdatePolicy(1, 120, 480), SliceAuth.SUPER_ADMIN, null);
		verify(auditService, never()).record(any(), any(), any(), any(), anyMap(), any());

		var updated = service.updatePolicy(2L, new UpdatePolicy(1, 90, 480), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown());
		assertThat(updated.firstResponseMinutes()).isEqualTo(90);
		verify(auditService).record(eq(AuditAction.SLA_POLICY_UPDATED), eq(1L), eq("SLA_POLICY"), any(),
				argThat(details -> details.toString().contains("firstResponseMinutes")
						&& !details.toString().contains("resolutionMinutes")),
				any());
	}

	private static Map<TicketPriority, SlaPolicy> policies() {
		Map<TicketPriority, SlaPolicy> policies = new EnumMap<>(TicketPriority.class);
		policies.put(TicketPriority.URGENT, policy(TicketPriority.URGENT, 60, 240));
		policies.put(TicketPriority.HIGH, policy(TicketPriority.HIGH, 120, 480));
		policies.put(TicketPriority.MEDIUM, policy(TicketPriority.MEDIUM, 240, 1440));
		policies.put(TicketPriority.LOW, policy(TicketPriority.LOW, 480, 2880));
		return policies;
	}

	private static SlaPolicy policy(TicketPriority priority, int firstResponse, int resolution) {
		SlaPolicy policy = new SlaPolicy();
		policy.setName(priority.name());
		policy.setPriority(priority);
		policy.setFirstResponseMinutes(firstResponse);
		policy.setResolutionMinutes(resolution);
		return policy;
	}

	/**
	 * @param respondedAfter minutes after start of the first response, or {@code null}
	 * @param resolvedAfter minutes after start of the resolution, or {@code null}
	 */
	private static Ticket ticket(String code, TicketPriority priority, Instant start, Integer respondedAfter, Integer resolvedAfter,
			TicketStatus status) {
		SlaPolicy policy = policies().get(priority);
		Ticket ticket = new Ticket();
		ReflectionTestUtils.setField(ticket, "id", (long) code.hashCode());
		ticket.setCode(code);
		ticket.setSubject("Ticket " + code);
		ticket.setDepartment(TestFixtures.department(1L, "IT", "IT"));
		ticket.setPriority(priority);
		ticket.setStatus(status);
		ticket.setSlaStartAt(start);
		ticket.setFirstResponseDueAt(start.plus(Duration.ofMinutes(policy.getFirstResponseMinutes())));
		ticket.setResolutionDueAt(start.plus(Duration.ofMinutes(policy.getResolutionMinutes())));
		ticket.setSlaWarningPct(75);
		if (respondedAfter != null) {
			ticket.setFirstRespondedAt(start.plus(Duration.ofMinutes(respondedAfter)));
		}
		if (resolvedAfter != null) {
			ticket.setResolvedAt(start.plus(Duration.ofMinutes(resolvedAfter)));
		}
		return ticket;
	}

	private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status,
			String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
