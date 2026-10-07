package com.teamops.task.dto;

/** What the viewer may do with this task, so the UI can hide controls. The server re-checks every action. */
public record TaskPermissions(boolean canEdit, boolean canAssign, boolean canCancel, boolean canComment,
		boolean canWatch) {

}
