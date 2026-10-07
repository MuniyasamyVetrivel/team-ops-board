package com.teamops.ticket.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ticket lifecycle (brief section 11). The SLA clock runs in NEW, OPEN and IN_PROGRESS and pauses in
 * WAITING_FOR_REQUESTER, RESOLVED and CLOSED. RESOLVED and CLOSED tickets can be reopened.
 */
public enum TicketStatus {

	NEW, OPEN, IN_PROGRESS, WAITING_FOR_REQUESTER, RESOLVED, CLOSED;

	/** Not yet resolved: counts as open work. */
	public static final Set<TicketStatus> OPEN_STATUSES = EnumSet.of(NEW, OPEN, IN_PROGRESS, WAITING_FOR_REQUESTER);

	private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.of(
			NEW, EnumSet.of(OPEN, IN_PROGRESS, WAITING_FOR_REQUESTER, RESOLVED, CLOSED),
			OPEN, EnumSet.of(IN_PROGRESS, WAITING_FOR_REQUESTER, RESOLVED, CLOSED),
			IN_PROGRESS, EnumSet.of(OPEN, WAITING_FOR_REQUESTER, RESOLVED, CLOSED),
			WAITING_FOR_REQUESTER, EnumSet.of(OPEN, IN_PROGRESS, RESOLVED, CLOSED),
			RESOLVED, EnumSet.of(OPEN, IN_PROGRESS, CLOSED),
			CLOSED, EnumSet.of(OPEN));

	public boolean isOpen() {
		return OPEN_STATUSES.contains(this);
	}

	/** Resolved or closed. */
	public boolean isDone() {
		return this == RESOLVED || this == CLOSED;
	}

	/** Whether the SLA clock runs in this status. */
	public boolean clockRuns() {
		return this == NEW || this == OPEN || this == IN_PROGRESS;
	}

	public boolean canMoveTo(TicketStatus next) {
		return TRANSITIONS.get(this).contains(next);
	}

	public Set<TicketStatus> nextStatuses() {
		return TRANSITIONS.get(this);
	}

}
