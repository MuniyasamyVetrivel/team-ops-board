package com.teamops.common.audit;

/**
 * A record that belongs to another one (a task's comment, an approval's step). Its audit entries also appear in the
 * parent's history.
 */
public interface AuditedChild {

	/** The parent record, or null when there is none. */
	AuditRef auditParent();

}
