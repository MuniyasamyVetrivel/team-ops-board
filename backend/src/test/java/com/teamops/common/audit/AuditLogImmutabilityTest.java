package com.teamops.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;

/** Audit entries cannot be edited or deleted from the app. */
class AuditLogImmutabilityTest {

	@Test
	void theRepositoryOnlyAddsAndReads() {
		assertThat(CrudRepository.class.isAssignableFrom(AuditLogRepository.class)).isFalse();
		assertThat(Arrays.stream(AuditLogRepository.class.getMethods()).map(Method::getName))
			.noneMatch(name -> name.toLowerCase(Locale.ROOT).matches("(delete|remove|update|modify).*"));
	}

	@Test
	void theEntityIsImmutableAndNotItselfAudited() {
		assertThat(AuditLog.class.isAnnotationPresent(Immutable.class)).isTrue();
		assertThat(RecordTypes.isAudited(AuditLog.class)).isFalse();
	}

}
