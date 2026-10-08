package com.teamops.notification.entity;

/** In-app notification kinds (brief section 65). Extend as modules are added (tickets, approvals, marketing). */
public enum NotificationType {

	TASK_ASSIGNED,
	TASK_DUE_SOON,
	TASK_OVERDUE,
	TICKET_ASSIGNED,
	/** Status change on a ticket you raised (e.g. resolved, waiting for you). */
	TICKET_UPDATED,
	/** A public reply on a ticket you raised or are assigned to. */
	TICKET_REPLY,
	/** A request is waiting for your decision. */
	APPROVAL_REQUIRED,
	/** Your request was approved or rejected. */
	APPROVAL_DECIDED,
	ANNOUNCEMENT_PUBLISHED

}
