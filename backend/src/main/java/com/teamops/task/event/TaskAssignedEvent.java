package com.teamops.task.event;

/**
 * Published inside the task transaction when a task gets a new assignee other than the person making the change.
 * Listeners run in the same transaction, so a rolled-back assignment never leaves a notification behind.
 */
public record TaskAssignedEvent(Long taskId, String code, String title, Long assigneeId, Long actorId,
		String actorName) {

}
