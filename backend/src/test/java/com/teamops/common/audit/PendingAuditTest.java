package com.teamops.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** A transaction leaves one entry per record and action, with the net change. */
class PendingAuditTest {

	private final PendingAudit pending = new PendingAudit();

	@Test
	void createdThenUpdatedIsOneCreatedEntryWithTheFinalValues() {
		RecordChange created = change(AuditAction.RECORD_CREATED, 7L);
		created.change("title", null, "Draft", null, false);
		created.change("code", null, null, null, false);
		pending.add(created);
		RecordChange updated = change(AuditAction.RECORD_UPDATED, 7L);
		updated.change("code", null, "TSK-000007", null, false);
		updated.label = "TSK-000007";
		pending.add(updated);

		List<RecordChange> entries = pending.drain();
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).action).isEqualTo(AuditAction.RECORD_CREATED);
		assertThat(entries.get(0).label).isEqualTo("TSK-000007");
		assertThat(entries.get(0).changes).containsOnlyKeys("title", "code");
		assertThat(entries.get(0).changes.get("code")).containsEntry("to", "TSK-000007");
	}

	@Test
	void twoUpdatesKeepTheFirstFromAndTheLastTo() {
		pending.add(update(7L, "title", "A", "B"));
		pending.add(update(7L, "title", "B", "C"));
		pending.add(update(7L, "status", "TODO", "IN_PROGRESS"));

		List<RecordChange> entries = pending.drain();
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).changes.get("title")).containsEntry("from", "A").containsEntry("to", "C");
		assertThat(entries.get(0).changes.get("status")).containsEntry("to", "IN_PROGRESS");
	}

	@Test
	void anEditThatIsUndoneLeavesNoEntry() {
		pending.add(update(7L, "title", "A", "B"));
		pending.add(update(7L, "title", "B", "A"));
		assertThat(pending.drain()).isEmpty();
	}

	@Test
	void aRecordCreatedAndDeletedInOneTransactionLeavesNothing() {
		RecordChange created = change(AuditAction.RECORD_CREATED, 7L);
		created.change("title", null, "Temp", null, false);
		pending.add(created);
		pending.add(change(AuditAction.RECORD_DELETED, 7L));
		assertThat(pending.drain()).isEmpty();
	}

	@Test
	void differentRecordsStaySeparateAndInOrder() {
		pending.add(update(7L, "title", "A", "B"));
		pending.add(update(8L, "title", "A", "B"));
		RecordChange deleted = change(AuditAction.RECORD_DELETED, 7L);
		deleted.change("title", "B", null, null, false);
		pending.add(deleted);

		List<RecordChange> entries = pending.drain();
		assertThat(entries).extracting(e -> e.entityId + ":" + e.action)
			.containsExactly("7:RECORD_UPDATED", "8:RECORD_UPDATED", "7:RECORD_DELETED");
		assertThat(pending.drain()).isEmpty();
	}

	@Test
	void aRedactedSecretAlwaysStaysWithoutValues() {
		RecordChange updated = change(AuditAction.RECORD_UPDATED, 7L);
		updated.redacted("passwordHash");
		pending.add(updated);

		List<RecordChange> entries = pending.drain();
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).changes.get("passwordHash")).isEqualTo(Map.of("redacted", true));
		assertThat(entries.get(0).details()).isEqualTo(Map.of("changes", Map.of("passwordHash", Map.of("redacted", true))));
	}

	@Test
	void financeFieldsFlagTheEntry() {
		RecordChange updated = change(AuditAction.RECORD_UPDATED, 7L);
		updated.change("budget", 100, 200, null, true);
		assertThat(updated.finance()).isTrue();
		assertThat(update(7L, "title", "A", "B").finance()).isFalse();
	}

	private static RecordChange update(Long id, String field, Object from, Object to) {
		RecordChange change = change(AuditAction.RECORD_UPDATED, id);
		change.change(field, from, to, null, false);
		return change;
	}

	private static RecordChange change(AuditAction action, Long id) {
		return new RecordChange(action, "TASK", id, null, 1L, null, null);
	}

}
