package com.teamops.common.sequence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

	@Test
	void padsTheNumberToTheConfiguredWidth() {
		assertThat(CodeGenerator.format("TSK", 6, 42)).isEqualTo("TSK-000042");
		assertThat(CodeGenerator.format("PRJ", 4, 1)).isEqualTo("PRJ-0001");
		assertThat(CodeGenerator.format("TKT", 6, 1234567)).as("never truncates").isEqualTo("TKT-1234567");
	}

}
