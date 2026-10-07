package com.teamops.ticket.service;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.user.entity.User;

/**
 * Ticket authorization for one actor (brief sections 4 and 11).
 * <ul>
 * <li><b>View</b>: Super Admin; the requester; the assignee; managers of the handling department; and agents (holders
 * of TICKET_EDIT) in the handling department.</li>
 * <li><b>Work</b> (change fields and status, write internal notes): TICKET_EDIT plus manager or agent of the handling
 * department, or the assignee.</li>
 * <li><b>Manage</b> (assign others, reopen closed tickets): TICKET_ASSIGN plus manager of the handling
 * department.</li>
 * <li><b>Requester</b>: may reply publicly, and confirm (close) or reopen a resolved ticket.</li>
 * </ul>
 */
public record TicketAccess(AuthenticatedUser actor, AccessScope scope) {

	static final String TICKET_EDIT = "TICKET_EDIT";

	static final String TICKET_ASSIGN = "TICKET_ASSIGN";

	public Long me() {
		return actor.id();
	}

	/** Departments whose tickets the actor sees as manager or agent (ignored for ALL). */
	public Set<Long> teamDepartmentIds() {
		Set<Long> ids = new HashSet<>(scope.departmentIds());
		if (actor.hasPermission(TICKET_EDIT)) {
			ids.add(actor.departmentId());
		}
		return ids;
	}

	public boolean canView(Ticket ticket) {
		return ticket.isRequestedBy(me()) || ticket.isAssignedTo(me()) || onTeam(ticket);
	}

	public boolean canWork(Ticket ticket) {
		return actor.hasPermission(TICKET_EDIT) && (onTeam(ticket) || ticket.isAssignedTo(me()));
	}

	public boolean canManage(Ticket ticket) {
		return actor.hasPermission(TICKET_ASSIGN) && scope.coversDepartment(ticket.getDepartment().getId());
	}

	/** {@code target == null} unassigns. Agents may take or drop a ticket themselves. */
	public boolean canAssign(Ticket ticket, User target) {
		if (target == null) {
			return canManage(ticket) || (ticket.isAssignedTo(me()) && canWork(ticket));
		}
		if (target.getId().equals(me())) {
			return canWork(ticket);
		}
		boolean eligible = target.getDepartment().getId().equals(ticket.getDepartment().getId())
				|| scope.coversUser(target.getId(), target.getDepartment().getId());
		return eligible && (canManage(ticket) || (actor.hasPermission(TICKET_ASSIGN) && canWork(ticket)));
	}

	/** Statuses the actor may move the ticket to from its current status. */
	public Set<TicketStatus> allowedStatuses(Ticket ticket) {
		TicketStatus current = ticket.getStatus();
		Set<TicketStatus> allowed = EnumSet.noneOf(TicketStatus.class);
		if (canWork(ticket)) {
			allowed.addAll(current.nextStatuses());
			if (current == TicketStatus.CLOSED && !canManage(ticket)) {
				allowed.clear();
			}
		}
		if (ticket.isRequestedBy(me()) && current == TicketStatus.RESOLVED) {
			allowed.add(TicketStatus.CLOSED);
			allowed.add(TicketStatus.OPEN);
		}
		return allowed;
	}

	private boolean onTeam(Ticket ticket) {
		Long departmentId = ticket.getDepartment().getId();
		return scope.coversDepartment(departmentId)
				|| (actor.hasPermission(TICKET_EDIT) && departmentId.equals(actor.departmentId()));
	}

}
