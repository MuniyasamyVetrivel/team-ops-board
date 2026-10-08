package com.teamops.common.csv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A data row: its line number in the file (header = line 1) and the raw cell text for each known column, in column
 * order.
 */
public record CsvRow(int line, Map<String, String> values) {

	public CsvRow {
		values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	/** The trimmed cell, or {@code null} when it is blank or the column is absent. */
	public String get(String column) {
		String value = values.get(column);
		return value == null || value.isBlank() ? null : value.strip();
	}

}
