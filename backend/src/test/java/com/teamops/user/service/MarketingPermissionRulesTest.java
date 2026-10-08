package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class MarketingPermissionRulesTest {

	@Test
	void consistentGrantsPass() {
		assertThatCode(() -> MarketingPermissionRules.assertConsistent(Set.of("TASK_VIEW"))).doesNotThrowAnyException();
		assertThatCode(() -> MarketingPermissionRules.assertConsistent(Set.of("MARKETING_VIEW")))
			.doesNotThrowAnyException();
		assertThatCode(() -> MarketingPermissionRules
			.assertConsistent(Set.of("MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT", "CONTENT_VIEW")))
			.doesNotThrowAnyException();
		assertThatCode(() -> MarketingPermissionRules.assertConsistent(MarketingPermissionRules.MARKETING_PERMISSIONS))
			.doesNotThrowAnyException();
	}

	@Test
	void marketingPermissionsNeedTheModulePermission() {
		assertThatThrownBy(() -> MarketingPermissionRules.assertConsistent(Set.of("SEO_VIEW", "TASK_VIEW")))
			.extracting("code")
			.isEqualTo("MARKETING_VIEW_REQUIRED");
	}

	@Test
	void editPermissionsNeedTheirViewPermission() {
		assertThatThrownBy(() -> MarketingPermissionRules
			.assertConsistent(Set.of("MARKETING_VIEW", "SEO_EDIT", "LEAD_EDIT", "LEAD_VIEW", "CONTENT_EDIT")))
			.hasMessage("Edit permissions need their view permission: CONTENT_EDIT needs CONTENT_VIEW, "
					+ "SEO_EDIT needs SEO_VIEW")
			.extracting("code")
			.isEqualTo("VIEW_PERMISSION_REQUIRED");
	}

}
