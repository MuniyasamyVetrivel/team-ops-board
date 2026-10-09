package com.teamops.common.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cell text for {@link ReportDocument} rows: plain numbers (so spreadsheets can sum them), ISO dates, and an empty
 * cell for a value that is missing or has nothing to divide by.
 */
public final class Cells {

	private Cells() {
	}

	public static String of(Number value) {
		if (value == null) {
			return "";
		}
		return value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : value.toString();
	}

	public static String of(LocalDate value) {
		return value == null ? "" : value.toString();
	}

	public static String of(String value) {
		return value == null ? "" : value;
	}

	public static String of(Enum<?> value) {
		return value == null ? "" : value.name();
	}

	public static String of(boolean value) {
		return value ? "Yes" : "No";
	}

}
