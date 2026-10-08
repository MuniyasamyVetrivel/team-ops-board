package com.teamops.task.event;

import com.teamops.task.entity.TaskStatus;

/**
 * Published inside the task transaction when a task's status changes. Listeners run in the same transaction, so their
 * changes (e.g. completing a recurring activity's occurrence and creating the next one) commit or roll back with it.
 */
public record TaskStatusChangedEvent(Long taskId, TaskStatus previous, TaskStatus status, Long actorId) {

}
