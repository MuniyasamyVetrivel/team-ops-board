package com.teamops.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.common.audit.AuditCatalog.BriefEvent;
import com.teamops.common.audit.AuditCatalog.Module;

class AuditCatalogTest {

	@Test
	void everyActionHasAModuleAndALabel() {
		for (AuditAction action : AuditAction.values()) {
			assertThat(AuditCatalog.moduleOf(action)).as(action.name()).isNotNull();
			assertThat(AuditCatalog.labelOf(action)).as(action.name()).isNotBlank();
		}
		assertThat(AuditCatalog.labelOf(AuditAction.TASK_STATUS_CHANGED)).isEqualTo("Task status changed");
		assertThat(AuditCatalog.labelOf(AuditAction.LOGIN)).isEqualTo("Signed in");
	}

	@Test
	void everyModuleHasActions() {
		for (Module module : Module.values()) {
			assertThat(AuditCatalog.actionsIn(module)).as(module.name()).isNotEmpty();
		}
	}

	/** Brief section 63, item by item. */
	@Test
	void theBriefsAuditEventsAreAllCovered() {
		assertThat(Arrays.stream(BriefEvent.values()).map(BriefEvent::label).toList()).containsExactly("User login",
				"Task creation", "Task assignment", "Task status change", "Ticket creation", "Ticket status change",
				"Target modification", "SEO ranking modification", "Campaign creation", "Campaign modification",
				"Lead modification", "Backlink modification", "User permission change");
		for (BriefEvent event : BriefEvent.values()) {
			assertThat(event.actions()).as(event.name()).isNotEmpty();
		}
		assertThat(BriefEvent.USER_PERMISSION_CHANGE.actions()).contains(AuditAction.USER_ACCESS_CHANGED,
				AuditAction.ROLE_PERMISSIONS_CHANGED);
		assertThat(BriefEvent.CAMPAIGN_CREATION.actions()).containsExactlyInAnyOrder(AuditAction.EMAIL_CAMPAIGN_CREATED,
				AuditAction.PAID_CAMPAIGN_CREATED);
		assertThat(AuditCatalog.eventsOf(AuditAction.TASK_ASSIGNED)).containsExactly(BriefEvent.TASK_ASSIGNMENT);
		assertThat(AuditCatalog.eventsOf(AuditAction.LOGOUT)).isEmpty();
	}

	@Test
	void eachBriefEventsActionsSitInOneModule() {
		Set<AuditAction> all = EnumSet.noneOf(AuditAction.class);
		Arrays.stream(BriefEvent.values()).forEach(e -> all.addAll(e.actions()));
		List<Module> modules = all.stream().map(AuditCatalog::moduleOf).distinct().toList();
		assertThat(modules).contains(Module.SIGN_IN, Module.TASKS, Module.TICKETS, Module.TARGETS, Module.SEO,
				Module.CAMPAIGNS, Module.LEADS, Module.BACKLINKS, Module.USERS);
	}

	@Test
	void parseOnlyKnowsRealActions() {
		assertThat(AuditCatalog.parse("LEAD_UPDATED")).contains(AuditAction.LEAD_UPDATED);
		assertThat(AuditCatalog.parse("DROP_TABLES")).isEmpty();
	}

}
