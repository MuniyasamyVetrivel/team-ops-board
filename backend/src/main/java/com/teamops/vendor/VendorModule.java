package com.teamops.vendor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.module.service.ModuleDefinition;
import com.teamops.user.entity.PermissionCodes;

/**
 * Vendor Management (Expansion Phase X7), a department tool: vendors, their contacts, contracts and contract
 * documents, and renewal reminders. Switched on for HR by V36; VENDOR_VIEW and VENDOR_EDIT are held by SUPER_ADMIN only
 * until granted from Admin settings.
 */
@Configuration(proxyBeanMethods = false)
public class VendorModule {

	public static final String KEY = "vendors";

	/** Where the module's API lives; {@code ModuleAccessInterceptor} guards everything below it. */
	public static final String API = "/api/modules/" + KEY;

	@Bean
	ModuleDefinition vendorModuleDefinition() {
		return new ModuleDefinition(KEY, "Vendors", "Store", "Vendors, contacts, contracts and renewals",
				PermissionCodes.VENDOR_VIEW, PermissionCodes.VENDOR_EDIT);
	}

}
