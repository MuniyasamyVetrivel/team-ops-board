package com.teamops.celebration.entity;

import com.teamops.common.audit.AuditRef;

/** The record an occasion's post or attachment belongs to, for audit history. */
final class OccasionSubjects {

	private OccasionSubjects() {
	}

	/** Birthdays and anniversaries belong to an employee; important days and events to themselves. */
	static AuditRef of(OccasionKind kind, Long subjectId) {
		if (kind == null) {
			return null;
		}
		return switch (kind) {
			case BIRTHDAY, WORK_ANNIVERSARY -> AuditRef.of(Employee.class, subjectId);
			case IMPORTANT_DAY -> AuditRef.of(ImportantDay.class, subjectId);
			case EVENT -> AuditRef.of(MarketingEvent.class, subjectId);
		};
	}

}
