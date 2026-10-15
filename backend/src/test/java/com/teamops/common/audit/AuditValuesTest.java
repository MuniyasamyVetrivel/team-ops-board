package com.teamops.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.task.entity.TaskPriority;

class AuditValuesTest {

	@Test
	void secretsAreRecognisedByName() {
		assertThat(AuditValues.isSensitive("passwordHash")).isTrue();
		assertThat(AuditValues.isSensitive("tokenHash")).isTrue();
		assertThat(AuditValues.isSensitive("apiKey")).isTrue();
		assertThat(AuditValues.isSensitive("clientSecret")).isTrue();
		assertThat(AuditValues.isSensitive("title")).isFalse();
		assertThat(AuditValues.isSensitive("mustChangePassword")).isTrue();
	}

	@Test
	void valuesBecomePlainJson() {
		assertThat(AuditValues.normalize(TaskPriority.HIGH)).isEqualTo("HIGH");
		assertThat(AuditValues.normalize(LocalDate.of(2026, 10, 16))).isEqualTo("2026-10-16");
		assertThat(AuditValues.normalize(Instant.parse("2026-10-16T05:30:00Z"))).isEqualTo("2026-10-16T05:30:00Z");
		assertThat(AuditValues.normalize(new BigDecimal("40.00"))).isEqualTo(new BigDecimal("40"));
		assertThat(AuditValues.normalize(new BigDecimal("12.50"))).isEqualTo(new BigDecimal("12.5"));
		assertThat(AuditValues.normalize(true)).isEqualTo(true);
		assertThat(AuditValues.normalize(Set.of(TaskPriority.LOW))).isEqualTo(List.of("LOW"));
		assertThat(AuditValues.normalize(new byte[] { 1, 2 })).isEqualTo("(binary, 2 bytes)");
	}

	@Test
	void longTextIsShortenedWithItsLength() {
		String body = "x".repeat(AuditValues.MAX_TEXT + 100);
		assertThat((String) AuditValues.normalize(body)).hasSize(AuditValues.MAX_TEXT + " (600 characters)".length() + 1)
			.endsWith("(600 characters)");
	}

	@Test
	void sameValuesIgnoreScaleAndOrder() {
		assertThat(AuditValues.same(new BigDecimal("40.0"), new BigDecimal("40.00"))).isTrue();
		assertThat(AuditValues.same(List.of(1L, 2L), List.of(2L, 1L))).isTrue();
		assertThat(AuditValues.same(List.of(1L), List.of(1L, 2L))).isFalse();
		assertThat(AuditValues.same(null, null)).isTrue();
		assertThat(AuditValues.same("a", null)).isFalse();
	}

}
