package com.teamops.common.audit;

import java.util.ArrayList;
import java.util.List;

/**
 * The record changes of one transaction, in order. Several flushes of the same record are folded together, so a
 * transaction leaves one entry per record and action:
 * <ul>
 * <li>created then updated → one "created" entry with the final values;</li>
 * <li>updated twice → one "updated" entry from the first value to the last (dropped if it ends where it began);</li>
 * <li>created then deleted → nothing (the record never existed outside the transaction).</li>
 * </ul>
 */
final class PendingAudit {

	private final List<RecordChange> entries = new ArrayList<>();

	void add(RecordChange change) {
		RecordChange open = latest(change.identity());
		if (open != null && change.action == AuditAction.RECORD_UPDATED
				&& (open.action == AuditAction.RECORD_CREATED || open.action == AuditAction.RECORD_UPDATED)) {
			change.changes.forEach(open::merge);
			if (change.label != null) {
				open.label = change.label;
			}
			if (change.parent != null) {
				open.parent = change.parent;
			}
			return;
		}
		if (open != null && open.action == AuditAction.RECORD_CREATED && isRemoval(change.action)) {
			entries.remove(open);
			return;
		}
		entries.add(change);
	}

	/** The entries to write, leaving out updates that changed nothing in the end; empties the buffer. */
	List<RecordChange> drain() {
		List<RecordChange> result = new ArrayList<>(entries.size());
		for (RecordChange entry : entries) {
			entry.dropUnchanged();
			if (entry.action == AuditAction.RECORD_UPDATED && entry.changes.isEmpty()) {
				continue;
			}
			result.add(entry);
		}
		entries.clear();
		return result;
	}

	private RecordChange latest(String identity) {
		for (RecordChange entry : entries.reversed()) {
			if (entry.identity().equals(identity)) {
				return entry;
			}
		}
		return null;
	}

	private static boolean isRemoval(AuditAction action) {
		return action == AuditAction.RECORD_DELETED || action == AuditAction.RECORD_PURGED;
	}

}
