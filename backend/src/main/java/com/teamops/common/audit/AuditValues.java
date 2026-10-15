package com.teamops.common.audit;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** How field values are written into audit diffs: plain JSON values, long text shortened, secrets never stored. */
public final class AuditValues {

	/** Longer text is cut here (article bodies, frozen report payloads); the diff notes the full length. */
	static final int MAX_TEXT = 500;

	private static final Pattern SENSITIVE = Pattern
		.compile(".*(password|passwd|secret|token|hash|salt|otp|apikey|api_key|credential|privatekey).*");

	private AuditValues() {
	}

	/** Passwords, tokens, hashes and secrets: the diff says they changed, never what they were. */
	public static boolean isSensitive(String field) {
		return SENSITIVE.matcher(field.toLowerCase(Locale.ROOT)).matches();
	}

	/**
	 * A JSON-friendly value: text (shortened), numbers, booleans, enum names, ISO dates; collections become lists.
	 * Entity references are resolved to ids by the caller first.
	 */
	public static Object normalize(Object value) {
		if (value == null || value instanceof Boolean || value instanceof Integer || value instanceof Long
				|| value instanceof Short) {
			return value;
		}
		if (value instanceof BigDecimal decimal) {
			BigDecimal stripped = decimal.stripTrailingZeros();
			return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
		}
		if (value instanceof Number number) {
			return number;
		}
		if (value instanceof Enum<?> e) {
			return e.name();
		}
		if (value instanceof TemporalAccessor || value instanceof UUID) {
			return value.toString();
		}
		if (value instanceof byte[] bytes) {
			return "(binary, " + bytes.length + " bytes)";
		}
		if (value instanceof Collection<?> collection) {
			return collection.stream().map(AuditValues::normalize).toList();
		}
		return shorten(value.toString());
	}

	static String shorten(String text) {
		if (text.length() <= MAX_TEXT) {
			return text;
		}
		return text.substring(0, MAX_TEXT) + "… (" + text.length() + " characters)";
	}

	/** Compares normalized values: 40.0 and 40.00 are the same amount, and lists compare as sets. */
	public static boolean same(Object from, Object to) {
		if (from instanceof BigDecimal a && to instanceof BigDecimal b) {
			return a.compareTo(b) == 0;
		}
		if (from instanceof List<?> a && to instanceof List<?> b) {
			return a.size() == b.size() && a.containsAll(b) && b.containsAll(a);
		}
		return Objects.equals(from, to);
	}

}
