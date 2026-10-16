package com.teamops.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;

class ModuleRegistryTest {

	static final ModuleDefinition BENCH = new ModuleDefinition("bench", "Bench Management", "Users",
			"Consultants, allocations and bench", "BENCH_VIEW", "BENCH_EDIT");

	static final ModuleDefinition VENDORS = new ModuleDefinition("vendors", "Vendors", "Building",
			"Vendors and contracts", "VENDOR_VIEW", "VENDOR_EDIT");

	@Test
	void modulesAreListedByNameAndFoundByKey() {
		ModuleRegistry registry = new ModuleRegistry(List.of(VENDORS, BENCH));

		assertThat(registry.all()).containsExactly(BENCH, VENDORS);
		assertThat(registry.find("vendors")).contains(VENDORS);
		assertThat(registry.find("finance")).isEmpty();
		assertThat(registry.find(null)).isEmpty();
		assertThat(registry.editNeedsView()).isEqualTo(Map.of("BENCH_EDIT", "BENCH_VIEW", "VENDOR_EDIT", "VENDOR_VIEW"));
		assertThatThrownBy(() -> registry.require("finance")).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo("MODULE_NOT_FOUND"));
	}

	@Test
	void noModulesIsFine() {
		assertThat(new ModuleRegistry(List.of()).all()).isEmpty();
	}

	@Test
	void keysAndPermissionsBelongToOneModule() {
		ModuleDefinition sameKey = new ModuleDefinition("bench", "Bench 2", "Users", "", "BENCH2_VIEW", "BENCH2_EDIT");
		assertThatThrownBy(() -> new ModuleRegistry(List.of(BENCH, sameKey))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("bench");

		ModuleDefinition borrowsPermission = new ModuleDefinition("talent", "Talent", "Users", "", "BENCH_VIEW",
				"TALENT_EDIT");
		assertThatThrownBy(() -> new ModuleRegistry(List.of(BENCH, borrowsPermission)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("BENCH_VIEW");
	}

	@Test
	void definitionsAreChecked() {
		assertThatThrownBy(() -> new ModuleDefinition("Bench", "Bench", "Users", "", "BENCH_VIEW", "BENCH_EDIT"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ModuleDefinition("bench/x", "Bench", "Users", "", "BENCH_VIEW", "BENCH_EDIT"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ModuleDefinition("bench", " ", "Users", "", "BENCH_VIEW", "BENCH_EDIT"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ModuleDefinition("bench", "Bench", "", "", "BENCH_VIEW", "BENCH_EDIT"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ModuleDefinition("bench", "Bench", "Users", "", "bench_view", "BENCH_EDIT"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ModuleDefinition("bench", "Bench", "Users", "", "BENCH_VIEW", "BENCH_VIEW"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
