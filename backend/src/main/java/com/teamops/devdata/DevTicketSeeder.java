package com.teamops.devdata;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.sla.entity.SlaPolicy;
import com.teamops.sla.repository.SlaPolicyRepository;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketCategory;
import com.teamops.ticket.entity.TicketComment;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.repository.TicketCategoryRepository;
import com.teamops.ticket.repository.TicketCommentRepository;
import com.teamops.ticket.repository.TicketRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Development help desk tickets (Phase 7). Runs only while the tickets table is empty. Times are relative to now so
 * the SLA page always shows a realistic mix: met and missed targets, tickets at risk, breached and paused ones.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevTicketSeeder {

	/**
	 * @param ageMinutes how long ago the ticket was raised
	 * @param respondedAfter minutes after raising of the first agent reply, or null
	 * @param doneAfter minutes after raising of the resolution (or, for WAITING, the start of the pause), or null
	 */
	record Seed(String subject, String category, TicketPriority priority, String requester, String assignee,
			TicketStatus status, int ageMinutes, Integer respondedAfter, Integer doneAfter) {
	}

	static final List<Seed> TICKETS = List.of(
			new Seed("Laptop will not boot after update", "Hardware", TicketPriority.URGENT, "karthik.raj@teamops.local", null, TicketStatus.NEW, 50, null, null),
			new Seed("VPN disconnects every hour", "Network", TicketPriority.HIGH, "priya.menon@teamops.local", "vignesh.raman@teamops.local", TicketStatus.IN_PROGRESS, 420, 45, null),
			new Seed("Need access to the finance shared drive", "Access Request", TicketPriority.MEDIUM, "divya.mohan@teamops.local", "vignesh.raman@teamops.local", TicketStatus.OPEN, 1_800, 200, null),
			new Seed("Outlook keeps asking for password", "Software", TicketPriority.MEDIUM, "meena.sekar@teamops.local", "vignesh.raman@teamops.local", TicketStatus.WAITING_FOR_REQUESTER, 900, 60, 120),
			new Seed("Install Figma on design laptop", "Software", TicketPriority.LOW, "pooja.lakshman@teamops.local", null, TicketStatus.NEW, 300, null, null),
			new Seed("Suspicious email asking for OTP", "Security Incident", TicketPriority.URGENT, "swetha.bala@teamops.local", "deepak.nair@teamops.local", TicketStatus.IN_PROGRESS, 150, 20, null),
			new Seed("Clarify new leave policy for probation", "HR Query", TicketPriority.LOW, "nithya.ganesh@teamops.local", "meena.sekar@teamops.local", TicketStatus.OPEN, 2_600, 400, null),
			new Seed("September reimbursement not received", "Payroll", TicketPriority.HIGH, "arun.kumar@teamops.local", "manoj.thomas@teamops.local", TicketStatus.OPEN, 200, 100, null),
			new Seed("Contact form returns 500 error", "Website Issue", TicketPriority.URGENT, "priya.menon@teamops.local", "karthik.raj@teamops.local", TicketStatus.IN_PROGRESS, 330, 30, null),
			new Seed("Banner for Diwali campaign", "Design Request", TicketPriority.MEDIUM, "kavya.suresh@teamops.local", "pooja.lakshman@teamops.local", TicketStatus.OPEN, 600, 90, null),
			new Seed("Update careers page job listings", "Website Issue", TicketPriority.LOW, "divya.mohan@teamops.local", "karthik.raj@teamops.local", TicketStatus.RESOLVED, 4_000, 120, 1_500),
			new Seed("Monitor flickering", "Hardware", TicketPriority.MEDIUM, "nithya.ganesh@teamops.local", "vignesh.raman@teamops.local", TicketStatus.CLOSED, 9_000, 60, 600),
			new Seed("Printer on 2nd floor jammed", "Hardware", TicketPriority.LOW, "meena.sekar@teamops.local", "vignesh.raman@teamops.local", TicketStatus.CLOSED, 14_000, 200, 2_000),
			new Seed("Reset MFA for new phone", "Access Request", TicketPriority.HIGH, "swetha.bala@teamops.local", "vignesh.raman@teamops.local", TicketStatus.CLOSED, 20_000, 30, 200),
			new Seed("Wi-Fi slow in meeting room B", "Network", TicketPriority.MEDIUM, "kavya.suresh@teamops.local", "vignesh.raman@teamops.local", TicketStatus.CLOSED, 26_000, 400, 2_000),
			new Seed("Phishing link clicked by mistake", "Security Incident", TicketPriority.URGENT, "arun.kumar@teamops.local", "deepak.nair@teamops.local", TicketStatus.CLOSED, 32_000, 15, 180),
			new Seed("Form 16 correction", "Payroll", TicketPriority.MEDIUM, "karthik.raj@teamops.local", "manoj.thomas@teamops.local", TicketStatus.CLOSED, 38_000, 300, 1_200),
			new Seed("SEO landing page copy update", "Marketing Request", TicketPriority.LOW, "gokul.ravi@teamops.local", "arun.kumar@teamops.local", TicketStatus.RESOLVED, 3_000, 500, 2_400),
			new Seed("Need a laptop stand", "Other", TicketPriority.LOW, "deepak.nair@teamops.local", null, TicketStatus.NEW, 100, null, null));

	private final TicketRepository ticketRepository;

	private final TicketCategoryRepository categoryRepository;

	private final TicketCommentRepository commentRepository;

	private final SlaPolicyRepository policyRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final CodeGenerator codeGenerator;

	private final AppSettingsService settings;

	private final BusinessCalendar calendar;

	private final JdbcTemplate jdbc;

	/**
	 * Help desk agents hold TICKET_EDIT (a direct grant on top of the EMPLOYEE role). Granted to every seeded assignee
	 * on each start, so databases seeded before Phase 7 get it too. Idempotent.
	 */
	@Transactional
	public int grantAgentAccess() {
		int granted = 0;
		for (String email : TICKETS.stream().map(Seed::assignee).filter(Objects::nonNull).distinct().toList()) {
			granted += jdbc.update("""
					insert ignore into user_permissions (user_id, permission_id)
					select u.id, p.id from users u join permissions p on p.code = 'TICKET_EDIT'
					where u.email = ?
					""", email);
		}
		return granted;
	}

	/** Returns the number of tickets created (0 when tickets already exist). */
	@Transactional
	public int seedIfEmpty() {
		if (ticketRepository.count() > 0) {
			return 0;
		}
		Instant now = calendar.now();
		Map<String, TicketCategory> categories = new HashMap<>();
		categoryRepository.findByActiveTrueOrderByNameAsc().forEach(c -> categories.put(c.getName(), c));
		Map<TicketPriority, SlaPolicy> policies = new HashMap<>();
		policyRepository.findAll().forEach(p -> policies.put(p.getPriority(), p));
		int created = 0;
		for (Seed seed : TICKETS) {
			User requester = userRepository.findByEmailIgnoreCase(seed.requester()).orElse(null);
			TicketCategory category = categories.get(seed.category());
			if (requester == null || category == null) {
				continue;
			}
			User assignee = seed.assignee() == null ? null
					: userRepository.findByEmailIgnoreCase(seed.assignee()).orElse(null);
			SlaPolicy policy = policies.get(seed.priority());
			Instant start = now.minus(Duration.ofMinutes(seed.ageMinutes()));

			Ticket ticket = new Ticket();
			ticket.setCode(codeGenerator.next(CodeGenerator.TICKET));
			ticket.setSubject(seed.subject());
			ticket.setDescription("Development seed ticket. " + seed.subject() + ".");
			ticket.setRequester(requester);
			ticket.setCategory(category);
			ticket.setDepartment(category.getDefaultDepartment() != null ? category.getDefaultDepartment()
					: departmentRepository.findByCode("HR").orElseThrow());
			ticket.setPriority(seed.priority());
			ticket.setAssignee(assignee);
			ticket.setStatus(seed.status());
			ticket.setSlaPolicy(policy);
			ticket.setSlaStartAt(start);
			ticket.setFirstResponseDueAt(start.plus(Duration.ofMinutes(policy.getFirstResponseMinutes())));
			ticket.setResolutionDueAt(start.plus(Duration.ofMinutes(policy.getResolutionMinutes())));
			ticket.setSlaWarningPct(settings.slaWarningThresholdPct());
			if (seed.respondedAfter() != null) {
				ticket.setFirstRespondedAt(start.plus(Duration.ofMinutes(seed.respondedAfter())));
			}
			if (seed.doneAfter() != null) {
				Instant done = start.plus(Duration.ofMinutes(seed.doneAfter()));
				ticket.setSlaPausedAt(done);
				if (seed.status().isDone()) {
					ticket.setResolvedAt(done);
					if (seed.status() == TicketStatus.CLOSED) {
						ticket.setClosedAt(done.plus(Duration.ofDays(1)).isBefore(now) ? done.plus(Duration.ofDays(1)) : now);
					}
				}
			}
			Ticket saved = ticketRepository.save(ticket);
			if (assignee != null && seed.respondedAfter() != null) {
				comment(saved, assignee, "Thanks for raising this. We are looking into it.");
			}
			if (seed.status() == TicketStatus.WAITING_FOR_REQUESTER && assignee != null) {
				comment(saved, assignee, "Could you share a screenshot of the error?");
			}
			ticketRepository.flush();
			jdbc.update("update tickets set created_at = ? where id = ?", Timestamp.from(start), saved.getId());
			created++;
		}
		return created;
	}

	private void comment(Ticket ticket, User author, String body) {
		TicketComment comment = new TicketComment();
		comment.setTicket(ticket);
		comment.setAuthor(author);
		comment.setBody(body);
		commentRepository.save(comment);
	}

}
