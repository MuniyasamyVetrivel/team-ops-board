package com.teamops.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.teamops.common.exception.ApiException;

class PasswordPolicyTest {

	@ParameterizedTest
	@ValueSource(strings = { "Welcome123", "correct horse 9", "S3cure!Passw0rd" })
	void acceptsPasswordsWithLettersAndDigits(String password) {
		assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@NullSource
	/** At least 10 characters, with letters and numbers. */
	@ValueSource(strings = { "", "Short1", "Welcome12", "onlyletters", "1234567890" })
	void rejectsWeakPasswords(String password) {
		assertThatThrownBy(() -> PasswordPolicy.validate(password)).isInstanceOf(ApiException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { 129, 500 })
	void rejectsOverlongPasswords(int length) {
		String password = "a1".repeat(length);

		assertThatThrownBy(() -> PasswordPolicy.validate(password)).isInstanceOf(ApiException.class);
	}

}
