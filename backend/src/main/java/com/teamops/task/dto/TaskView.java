package com.teamops.task.dto;

/** Relationship filter applied on top of the viewer's access scope. */
public enum TaskView {

	/** Everything the viewer may see. */
	ALL,
	ASSIGNED_TO_ME,
	CREATED_BY_ME,
	WATCHING

}
