package com.teamops.finance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.module.service.ModuleDefinition;
import com.teamops.user.entity.PermissionCodes;

/**
 * Finance Tracker (Expansion Phase X8), a department tool: tools and licences, exchange rates to AUD and renewal
 * reminders (part 1); expenses, budgets and the dashboard follow. Switched on for HR by V37. FINANCE_VIEW (which also
 * shows finance amounts elsewhere) and FINANCE_EDIT are held by SUPER_ADMIN only until granted from Admin settings.
 */
@Configuration(proxyBeanMethods = false)
public class FinanceModule {

	public static final String KEY = "finance";

	/** Where the module's API lives; {@code ModuleAccessInterceptor} guards everything below it. */
	public static final String API = "/api/modules/" + KEY;

	/** The base currency: exchange rates convert to it, and it is never stored as a rate. */
	public static final String BASE_CURRENCY = "AUD";

	@Bean
	ModuleDefinition financeModuleDefinition() {
		return new ModuleDefinition(KEY, "Finance Tracker", "Wallet",
				"Tools and licences, exchange rates, expenses and budgets", PermissionCodes.FINANCE_VIEW,
				PermissionCodes.FINANCE_EDIT);
	}

}
