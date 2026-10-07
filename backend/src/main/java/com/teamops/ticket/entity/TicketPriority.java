package com.teamops.ticket.entity;

/** Ticket priorities; each has one SLA policy. {@link #rank()} orders them for sorting (URGENT highest). */
public enum TicketPriority {

	LOW, MEDIUM, HIGH, URGENT;

	public int rank() {
		return ordinal() + 1;
	}

}
