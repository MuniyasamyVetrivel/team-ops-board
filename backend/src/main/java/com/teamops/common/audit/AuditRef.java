package com.teamops.common.audit;

/** A record in the audit log: its entity type ({@link RecordTypes#typeOf(Class)}) and id. */
public record AuditRef(String type, Long id) {

	public static AuditRef of(Class<?> entityClass, Long id) {
		return id == null ? null : new AuditRef(RecordTypes.typeOf(entityClass), id);
	}

}
