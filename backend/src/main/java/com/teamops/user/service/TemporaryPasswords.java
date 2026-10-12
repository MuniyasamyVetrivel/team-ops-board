package com.teamops.user.service;

import java.security.SecureRandom;

import com.teamops.common.security.PasswordPolicy;

/**
 * Random one-time passwords for admin-created accounts. They meet {@link PasswordPolicy} and leave out characters
 * that are easy to misread (0/O, 1/l/I), since people type them from a file.
 */
public final class TemporaryPasswords {

	static final int LENGTH = 14;

	private static final String LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";

	private static final String DIGITS = "23456789";

	private static final String ALL = LETTERS + DIGITS;

	private static final SecureRandom RANDOM = new SecureRandom();

	private TemporaryPasswords() {
	}

	public static String generate() {
		char[] password = new char[LENGTH];
		password[0] = pick(LETTERS);
		password[1] = pick(DIGITS);
		for (int i = 2; i < LENGTH; i++) {
			password[i] = pick(ALL);
		}
		for (int i = LENGTH - 1; i > 0; i--) {
			int j = RANDOM.nextInt(i + 1);
			char swap = password[i];
			password[i] = password[j];
			password[j] = swap;
		}
		return new String(password);
	}

	private static char pick(String alphabet) {
		return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
	}

}
