package com.teamops.ticket.repository;

/** Aggregate projection for the "My Tickets" summary. */
public interface MyTicketCounts {

	long getRequestedOpen();

	long getWaitingOnMe();

	long getResolvedToConfirm();

	long getAssignedOpen();

}
