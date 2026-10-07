package com.teamops.sla.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.ClientInfo;
import com.teamops.sla.dto.SlaDtos.PolicyResponse;
import com.teamops.sla.dto.SlaDtos.PriorityRow;
import com.teamops.sla.dto.SlaDtos.Summary;
import com.teamops.sla.dto.SlaDtos.TicketKpis;
import com.teamops.sla.dto.SlaDtos.UpdatePolicy;
import com.teamops.sla.entity.SlaPolicy;
import com.teamops.sla.repository.SlaPolicyRepository;
import com.teamops.ticket.dto.TicketDtos.TicketListItem;
import com.teamops.ticket.dto.TicketDtos.TicketSla;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.ticket.repository.TicketSpecifications;
import com.teamops.ticket.service.TicketService;

import lombok.RequiredArgsConstructor;

/**
 * SLA policies, compliance and at-risk tickets (brief section 12). Figures are computed with {@link SlaCalculator}
 * over the tickets the viewer can see, so the SLA page, the ticket list and the dashboard always agree.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlaService {

	public static final int MAX_WINDOW_DAYS = 365;

	static final int AT_RISK_LIMIT = 15;

	private final SlaPolicyRepository policyRepository;

	private final TicketRepository ticketRepository;

	private final TicketService ticketService;

	private final AppSettingsService settings;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	public List<PolicyResponse> policies() {
		return policyRepository.findAllByOrderByResolutionMinutesAsc().stream().map(PolicyResponse::of).toList();
	}

	@Transactional
	public PolicyResponse updatePolicy(Long id, UpdatePolicy request, AuthenticatedUser actor, ClientInfo client) {
		SlaPolicy policy = policyRepository.findById(id)
			.orElseThrow(() -> ApiException.notFound("SLA_POLICY_NOT_FOUND", "SLA policy not found"));
		if (!Objects.equals(policy.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this policy just now. Reload and try again.");
		}
		if (request.resolutionMinutes() < request.firstResponseMinutes()) {
			throw ApiException.badRequest("INVALID_SLA",
					"The resolution time cannot be shorter than the first response time");
		}
		AuditChanges changes = new AuditChanges()
			.track("firstResponseMinutes", policy.getFirstResponseMinutes(), request.firstResponseMinutes())
			.track("resolutionMinutes", policy.getResolutionMinutes(), request.resolutionMinutes());
		if (changes.isEmpty()) {
			return PolicyResponse.of(policy);
		}
		policy.setFirstResponseMinutes(request.firstResponseMinutes());
		policy.setResolutionMinutes(request.resolutionMinutes());
		policyRepository.flush();
		auditService.record(AuditAction.SLA_POLICY_UPDATED, actor.id(), "SLA_POLICY", policy.getId(),
				changes.toDetails(), client);
		return PolicyResponse.of(policy);
	}

	public Summary summary(int windowDays, AuthenticatedUser actor) {
		if (windowDays < 1 || windowDays > MAX_WINDOW_DAYS) {
			throw ApiException.badRequest("INVALID_WINDOW", "The window must be between 1 and " + MAX_WINDOW_DAYS + " days");
		}
		Instant now = calendar.now();
		Instant since = calendar.startOf(calendar.today().minusDays(windowDays - 1L));
		List<Ticket> tickets = ticketRepository.findAll(TicketSpecifications.visibleTo(ticketService.access(actor))
			.and(TicketSpecifications.openOrCreatedSince(since)));
		Map<TicketPriority, SlaPolicy> policies = new EnumMap<>(TicketPriority.class);
		policyRepository.findAll().forEach(policy -> policies.put(policy.getPriority(), policy));
		return summarise(tickets, policies, since, now, windowDays, settings.slaWarningThresholdPct());
	}

	/** Open tickets and breached open tickets the viewer can see (dashboard KPIs). */
	public TicketKpis openTicketKpis(AuthenticatedUser actor) {
		Instant now = calendar.now();
		List<Ticket> open = ticketRepository.findAll(TicketSpecifications.visibleTo(ticketService.access(actor))
			.and(TicketSpecifications.statusIn(TicketStatus.OPEN_STATUSES)));
		long breached = open.stream().filter(t -> TicketSla.of(t, now).overall() == SlaState.BREACHED).count();
		return new TicketKpis(open.size(), breached);
	}

	/**
	 * Compliance counts tickets created since {@code since}; open-state counts and the at-risk list use every open
	 * ticket, whatever its age.
	 */
	static Summary summarise(List<Ticket> tickets, Map<TicketPriority, SlaPolicy> policies, Instant since,
			Instant now, int windowDays, int warningPct) {
		List<SlaCalculator.Status> firstAll = new ArrayList<>();
		List<SlaCalculator.Status> resolutionAll = new ArrayList<>();
		Map<TicketPriority, List<SlaCalculator.Status>> firstByPriority = new EnumMap<>(TicketPriority.class);
		Map<TicketPriority, List<SlaCalculator.Status>> resolutionByPriority = new EnumMap<>(TicketPriority.class);
		Map<TicketPriority, Long> createdByPriority = new EnumMap<>(TicketPriority.class);
		Map<TicketPriority, Long> breachedByPriority = new EnumMap<>(TicketPriority.class);
		long onTrack = 0;
		long warning = 0;
		long breached = 0;
		long paused = 0;
		List<TicketListItem> atRisk = new ArrayList<>();

		for (Ticket ticket : tickets) {
			TicketSla sla = TicketSla.of(ticket, now);
			TicketPriority priority = ticket.getPriority();
			if (!ticket.getSlaStartAt().isBefore(since)) {
				firstAll.add(sla.firstResponse());
				resolutionAll.add(sla.resolution());
				firstByPriority.computeIfAbsent(priority, p -> new ArrayList<>()).add(sla.firstResponse());
				resolutionByPriority.computeIfAbsent(priority, p -> new ArrayList<>()).add(sla.resolution());
				createdByPriority.merge(priority, 1L, Long::sum);
			}
			if (ticket.getStatus().isOpen()) {
				switch (sla.overall()) {
					case ON_TRACK -> onTrack++;
					case WARNING -> warning++;
					case BREACHED -> {
						breached++;
						breachedByPriority.merge(priority, 1L, Long::sum);
					}
				}
				if (sla.resolution().paused()) {
					paused++;
				}
				if (sla.overall() != SlaState.ON_TRACK) {
					atRisk.add(TicketListItem.of(ticket, now));
				}
			}
		}
		atRisk.sort(Comparator.comparing((TicketListItem item) -> item.sla().overall()).reversed()
			.thenComparing(item -> item.sla().resolution().dueAt()));

		List<PriorityRow> rows = new ArrayList<>();
		for (TicketPriority priority : List.of(TicketPriority.URGENT, TicketPriority.HIGH, TicketPriority.MEDIUM,
				TicketPriority.LOW)) {
			SlaPolicy policy = policies.get(priority);
			rows.add(new PriorityRow(priority, policy == null ? 0 : policy.getFirstResponseMinutes(),
					policy == null ? 0 : policy.getResolutionMinutes(), createdByPriority.getOrDefault(priority, 0L),
					SlaCalculator.compliancePercent(firstByPriority.getOrDefault(priority, List.of())),
					SlaCalculator.compliancePercent(resolutionByPriority.getOrDefault(priority, List.of())),
					breachedByPriority.getOrDefault(priority, 0L)));
		}
		return new Summary(windowDays, now, warningPct, firstAll.size(), SlaCalculator.compliancePercent(firstAll),
				SlaCalculator.compliancePercent(resolutionAll), onTrack + warning + breached, onTrack, warning,
				breached, paused, rows, atRisk.stream().limit(AT_RISK_LIMIT).toList());
	}

}
