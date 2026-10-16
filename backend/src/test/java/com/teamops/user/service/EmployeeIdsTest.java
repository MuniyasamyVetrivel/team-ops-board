package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;

class EmployeeIdsTest {

	@Test
	void keepsTheIdAsTypedWithoutSurroundingSpaces() {
		assertThat(EmployeeIds.normalise(" E-1042 ")).isEqualTo("E-1042");
		assertThat(EmployeeIds.normalise("TRI/2026/07")).isEqualTo("TRI/2026/07");
		assertThat(EmployeeIds.normalise("a.b_c-9")).isEqualTo("a.b_c-9");
		assertThat(EmployeeIds.normalise("EMP170_IN")).isEqualTo("EMP170_IN");
		assertThat(EmployeeIds.normalise("EMP100_AU")).isEqualTo("EMP100_AU");
	}

	@Test
	void refusesBlankAndMalformedIds() {
		assertCode(null, "EMPLOYEE_ID_REQUIRED");
		assertCode("   ", "EMPLOYEE_ID_REQUIRED");
		assertCode("-E1", "INVALID_EMPLOYEE_ID");
		assertCode("E 1", "INVALID_EMPLOYEE_ID");
		assertCode("E1<script>", "INVALID_EMPLOYEE_ID");
		assertCode("E".repeat(41), "INVALID_EMPLOYEE_ID");
		assertThat(EmployeeIds.normalise("E".repeat(40))).hasSize(40);
	}

	@Test
	void comparesWithoutRegardToCase() {
		assertThat(EmployeeIds.same("e-7", " E-7")).isTrue();
		assertThat(EmployeeIds.same("E-7", "E-8")).isFalse();
		assertThat(EmployeeIds.same(null, null)).isTrue();
		assertThat(EmployeeIds.same("E-7", null)).isFalse();
	}

	private static void assertCode(String value, String code) {
		assertThatThrownBy(() -> EmployeeIds.normalise(value))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
	}

}
