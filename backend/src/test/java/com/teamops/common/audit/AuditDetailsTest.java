package com.teamops.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Finance amounts are logged in full and shown only to users who can see finance. */
class AuditDetailsTest {

	@Test
	void aMoneyFieldKeepsItsNameButLosesItsValues() {
		Map<String, Object> details = Map.of("changes",
				Map.of("budget", Map.of("from", 1000, "to", 1500, "finance", true), "name",
						Map.of("from", "Q3", "to", "Q4")));

		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<String, Object>>> hidden = (Map<String, Map<String, Map<String, Object>>>) AuditDetails
			.forViewer(details, AuditAction.RECORD_UPDATED, false);
		assertThat(hidden.get("changes").get("budget")).containsEntry("hidden", true).doesNotContainKeys("from", "to");
		assertThat(hidden.get("changes").get("name")).containsEntry("from", "Q3").containsEntry("to", "Q4");
		// The stored document is untouched, and finance viewers see everything.
		assertThat(AuditDetails.forViewer(details, AuditAction.RECORD_UPDATED, true)).isSameAs(details);
	}

	@Test
	void businessEventsHideTheirKnownAmounts() {
		Map<String, Object> submitted = new HashMap<>(Map.of("code", "APR-000001", "amount", "42000.00"));
		@SuppressWarnings("unchecked")
		Map<String, Object> hidden = (Map<String, Object>) AuditDetails.forViewer(submitted,
				AuditAction.APPROVAL_SUBMITTED, false);
		assertThat(hidden).containsEntry("code", "APR-000001").containsEntry("amount", null);
		assertThat(hidden.get("hiddenFields")).isEqualTo(List.of("amount"));
		assertThat(submitted).containsEntry("amount", "42000.00");

		Map<String, Object> corrected = Map.of("changes", Map.of("results",
				Map.of("from", Map.of("spend", 100, "clicks", 5), "to", Map.of("spend", 120, "clicks", 6))));
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<String, Map<String, Object>>>> masked = (Map<String, Map<String, Map<String, Map<String, Object>>>>) AuditDetails
			.forViewer(corrected, AuditAction.PAID_RESULTS_CORRECTED, false);
		assertThat(masked.get("changes").get("results").get("from")).containsEntry("spend", null)
			.containsEntry("clicks", 5);
		assertThat(masked.get("changes").get("results").get("to")).containsEntry("spend", null)
			.containsEntry("clicks", 6);
	}

	@Test
	void aWholeChangeOfAMoneyFieldIsHidden() {
		Map<String, Object> updated = Map.of("changes", Map.of("budget", Map.of("from", 1, "to", 2)));
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<String, Object>>> masked = (Map<String, Map<String, Map<String, Object>>>) AuditDetails
			.forViewer(updated, AuditAction.PAID_CAMPAIGN_UPDATED, false);
		assertThat(masked.get("changes").get("budget")).isEqualTo(Map.of("hidden", true));
	}

	@Test
	void entriesWithAmountsAreFlagged() {
		assertThat(AuditDetails.hasFinance(AuditAction.APPROVAL_SUBMITTED, Map.of())).isTrue();
		assertThat(AuditDetails.hasFinance(AuditAction.RECORD_UPDATED,
				Map.of("changes", Map.of("amount", Map.of("from", 1, "to", 2, "finance", true))))).isTrue();
		assertThat(AuditDetails.hasFinance(AuditAction.RECORD_UPDATED,
				Map.of("changes", Map.of("title", Map.of("from", "a", "to", "b"))))).isFalse();
		assertThat(AuditDetails.hasFinance(AuditAction.LOGIN, null)).isFalse();
	}

}
