package com.teamops.approval.entity;

/** Who decides a step: the requester's department manager, any holder of a role, or a named user. */
public enum ApproverKind {

	DEPARTMENT_MANAGER, ROLE, USER

}
