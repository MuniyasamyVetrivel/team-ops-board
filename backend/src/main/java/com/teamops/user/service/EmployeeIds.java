package com.teamops.user.service;

import java.util.Objects;
import java.util.regex.Pattern;

import com.teamops.common.exception.ApiException;

/**
 * Employee ID rules, shared by users and the user CSV import: 1 to 40 characters, starting with a letter or digit,
 * then letters, digits, dot, dash, underscore or slash (for example {@code E-1042}, {@code TRI/2026/07}). Kept as
 * typed, trimmed; compared without regard to case, as the database does.
 */
public final class EmployeeIds {

	public static final int MAX_LENGTH = 40;

	static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0," + (MAX_LENGTH - 1) + "}");

	public static final String DESCRIPTION = "up to " + MAX_LENGTH
			+ " letters, digits, dots, dashes, underscores or slashes, starting with a letter or digit";

	private EmployeeIds() {
	}

	/** The trimmed ID; 400 {@code EMPLOYEE_ID_REQUIRED} when blank, {@code INVALID_EMPLOYEE_ID} when malformed. */
	public static String normalise(String value) {
		String id = value == null ? "" : value.trim();
		if (id.isEmpty()) {
			throw ApiException.badRequest("EMPLOYEE_ID_REQUIRED", "Employee ID is required");
		}
		if (!FORMAT.matcher(id).matches()) {
			throw ApiException.badRequest("INVALID_EMPLOYEE_ID", "Employee ID must be " + DESCRIPTION);
		}
		return id;
	}

	/** Same ID, ignoring case and surrounding spaces (null-safe). */
	public static boolean same(String a, String b) {
		return Objects.equals(a == null ? null : a.trim().toUpperCase(java.util.Locale.ROOT),
				b == null ? null : b.trim().toUpperCase(java.util.Locale.ROOT));
	}

}
