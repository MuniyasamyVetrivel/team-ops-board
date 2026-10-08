package com.teamops.common.csv;

import java.util.Locale;

/**
 * One column an importer understands. Header matching ignores case, surrounding spaces, and treats spaces and hyphens
 * as underscores, so "Search Volume" matches {@code search_volume}.
 */
public record CsvColumn(String name, boolean required, String description, String example) {

	public static CsvColumn required(String name, String description, String example) {
		return new CsvColumn(name, true, description, example);
	}

	public static CsvColumn optional(String name, String description, String example) {
		return new CsvColumn(name, false, description, example);
	}

	/** The comparable form of a header cell. */
	static String normalise(String header) {
		return header == null ? "" : header.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
	}

}
