package com.teamops.bench;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.module.service.ModuleDefinition;
import com.teamops.user.entity.PermissionCodes;

/**
 * Project Allocation and Bench Management (Expansion Phase X6), a department tool: consultants, clients, client
 * engagements and (part 2) allocations and the bench. Switched on for HR and Talent Acquisition by V34; BENCH_VIEW and
 * BENCH_EDIT are held by SUPER_ADMIN only until granted from Admin settings.
 */
@Configuration(proxyBeanMethods = false)
public class BenchModule {

	public static final String KEY = "bench";

	/** Where the module's API lives; {@code ModuleAccessInterceptor} guards everything below it. */
	public static final String API = "/api/modules/" + KEY;

	@Bean
	ModuleDefinition benchModuleDefinition() {
		return new ModuleDefinition(KEY, "Bench Management", "Users",
				"Consultants, client engagements, allocations and the bench", PermissionCodes.BENCH_VIEW,
				PermissionCodes.BENCH_EDIT);
	}

}
