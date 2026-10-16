package com.teamops.vendor.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.util.StringUtils;

import com.teamops.common.exception.ApiException;

/** Text rules shared by the vendor forms and the CSV imports. */
final class VendorText {

	static final String DEFAULT_CURRENCY = "INR";

	private VendorText() {
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

	/** Whether a value is an absolute http(s) address with a host. */
	static boolean isWebsite(String value) {
		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
		}
		catch (URISyntaxException ex) {
			return false;
		}
	}

	/** Trimmed website, or null; 400 INVALID_WEBSITE unless it is an http(s) address. */
	static String website(String value) {
		String website = trimToNull(value);
		if (website != null && !isWebsite(website)) {
			throw ApiException.badRequest("INVALID_WEBSITE", "The website must start with http:// or https://");
		}
		return website;
	}

	/** An ISO currency code in upper case; INR when blank. */
	static String currency(String value) {
		String currency = trimToNull(value);
		return currency == null ? DEFAULT_CURRENCY : currency.toUpperCase(Locale.ROOT);
	}

	/** Lower-cased email, or null when blank. */
	static String email(String value) {
		String email = trimToNull(value);
		return email == null ? null : email.toLowerCase(Locale.ROOT);
	}

}
