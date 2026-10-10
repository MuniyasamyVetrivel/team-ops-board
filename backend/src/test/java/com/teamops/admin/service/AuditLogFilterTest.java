package com.teamops.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.admin.dto.AuditLogDtos.Filter;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditCatalog.BriefEvent;
import com.teamops.common.audit.AuditCatalog.Module;

/** Action, module and brief-event filters narrow each other. */
class AuditLogFilterTest {

	@Test
	void noActionFilterMeansEveryAction() {
		assertThat(AuditLogService.actionsFor(filter(null, null, null))).isNull();
	}

	@Test
	void aModuleSelectsItsActions() {
		assertThat(AuditLogService.actionsFor(filter(null, Module.LEADS, null))).containsExactlyInAnyOrder(
				AuditAction.LEAD_CREATED, AuditAction.LEAD_UPDATED, AuditAction.LEAD_STATUS_CHANGED,
				AuditAction.LEAD_DELETED);
	}

	@Test
	void filtersIntersect() {
		assertThat(AuditLogService.actionsFor(filter(null, Module.CAMPAIGNS, BriefEvent.CAMPAIGN_CREATION)))
			.containsExactlyInAnyOrder(AuditAction.EMAIL_CAMPAIGN_CREATED, AuditAction.PAID_CAMPAIGN_CREATED);
		assertThat(AuditLogService.actionsFor(
				filter(Set.of(AuditAction.TASK_CREATED, AuditAction.LOGIN), null, BriefEvent.USER_LOGIN)))
			.containsExactly(AuditAction.LOGIN);
		// Nothing can match a task action inside the Leads module.
		assertThat(AuditLogService.actionsFor(filter(Set.of(AuditAction.TASK_CREATED), Module.LEADS, null))).isEmpty();
	}

	private static Filter filter(Set<AuditAction> actions, Module module, BriefEvent event) {
		return new Filter(actions, module, event, null, null, null, null, null, null);
	}

}
