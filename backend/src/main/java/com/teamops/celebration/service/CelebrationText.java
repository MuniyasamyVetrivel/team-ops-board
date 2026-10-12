package com.teamops.celebration.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.util.StringUtils;

import com.teamops.common.exception.ApiException;

/** Input clean-up shared by the celebration services. */
final class CelebrationText {

	private CelebrationText() {
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

	/** An optional absolute http(s) link, trimmed; 400 {@code INVALID_LINK} otherwise. */
	static String link(String value, String label) {
		String link = trimToNull(value);
		if (link == null) {
			return null;
		}
		try {
			URI uri = new URI(link);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if ((scheme.equals("http") || scheme.equals("https")) && StringUtils.hasText(uri.getHost())) {
				return link;
			}
		}
		catch (URISyntaxException ex) {
			// reported below
		}
		throw ApiException.badRequest("INVALID_LINK", label + " must be a full http:// or https:// link");
	}

}
