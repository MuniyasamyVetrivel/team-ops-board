package com.teamops.finance.service;

import java.util.Locale;

import org.springframework.util.StringUtils;

/** Text rules shared by the finance forms and the CSV imports. */
final class FinanceText {

	private FinanceText() {
	}

	/** Trimmed text, or null when blank. */
	static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	/** A case-insensitive "contains" LIKE pattern with {@code \} as the escape character. */
	static String containsPattern(String search) {
		return "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
				+ "%";
	}

	/** An ISO currency code in upper case; {@code fallback} when blank. */
	static String currency(String value, String fallback) {
		String currency = trimToNull(value);
		return currency == null ? fallback : currency.toUpperCase(Locale.ROOT);
	}

}
