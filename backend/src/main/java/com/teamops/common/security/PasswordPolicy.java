package com.teamops.common.security;

import com.teamops.common.exception.ApiException;

/** Password rules for passwords set through the API (admin create / reset). */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	public static final int MAX_LENGTH = 128;

	public static final String DESCRIPTION = "at least " + MIN_LENGTH + " characters, including a letter and a number";

	private PasswordPolicy() {
	}

	public static void validate(String password) {
		boolean valid = password != null && password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH
				&& password.chars().anyMatch(Character::isLetter) && password.chars().anyMatch(Character::isDigit);
		if (!valid) {
			throw ApiException.badRequest("WEAK_PASSWORD", "Password must be " + DESCRIPTION);
		}
	}

}
