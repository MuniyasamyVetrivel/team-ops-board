package com.teamops.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.teamops.common.exception.ApiException;

class PasswordPolicyTest {

	@ParameterizedTest
	@ValueSource(strings = { "Welcome1", "correct horse 9", "S3cure!Passw0rd" })
	void acceptsPasswordsWithLettersAndDigits(String password) {
		assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "Short1", "onlyletters", "12345678" })
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
