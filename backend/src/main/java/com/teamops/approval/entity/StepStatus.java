package com.teamops.approval.entity;

/** WAITING: not reached yet. PENDING: the current step. SKIPPED: not needed (self-approval, rejection, cancel). */
public enum StepStatus {

	WAITING, PENDING, APPROVED, REJECTED, SKIPPED

}
