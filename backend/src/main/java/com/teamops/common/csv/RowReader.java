package com.teamops.common.csv;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Typed access to one CSV row that collects every problem instead of stopping at the first, so the preview can show
 * all errors at once. Each getter returns {@code null} when the cell is blank or invalid; a blank required cell is
 * reported as an error. Importers call the getters, add their own checks with {@link #error}, and only build a
 * record when {@link #valid()}.
 */
public class RowReader {

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final CsvRow row;

	private final Map<String, CsvColumn> columns;

	private final List<ImportDtos.CellError> errors = new ArrayList<>();

	public RowReader(CsvRow row, List<CsvColumn> columns) {
		this.row = row;
		this.columns = columns.stream().collect(Collectors.toMap(CsvColumn::name, column -> column));
	}

	public int line() {
		return row.line();
	}

	public CsvRow row() {
		return row;
	}

	public boolean valid() {
		return errors.isEmpty();
	}

	public List<ImportDtos.CellError> errors() {
		return List.copyOf(errors);
	}

	/** Records a problem with a column ({@code null} for the row as a whole). */
	public void error(String column, String message) {
		errors.add(new ImportDtos.CellError(column, message));
	}

	public String text(String column, int maxLength) {
		String value = cell(column);
		if (value != null && value.length() > maxLength) {
			error(column, "Must be at most " + maxLength + " characters");
			return null;
		}
		return value;
	}

	public Integer integer(String column, int min, int max) {
		String value = cell(column);
		if (value == null) {
			return null;
		}
		try {
			int number = Integer.parseInt(value.replace(",", ""));
			if (number < min || number > max) {
				error(column, "Must be between " + min + " and " + max);
				return null;
			}
			return number;
		}
		catch (NumberFormatException ex) {
			error(column, "Must be a whole number");
			return null;
		}
	}

	/** A non-negative amount with at most {@code scale} decimals, e.g. 42000.50. Thousands separators are allowed. */
	public BigDecimal amount(String column, int scale) {
		String value = cell(column);
		if (value == null) {
			return null;
		}
		try {
			BigDecimal number = new BigDecimal(value.replace(",", ""));
			if (number.signum() < 0) {
				error(column, "Cannot be negative");
				return null;
			}
			if (number.stripTrailingZeros().scale() > scale) {
				error(column, "At most " + scale + " decimal places");
				return null;
			}
			return number;
		}
		catch (NumberFormatException ex) {
			error(column, "Must be a number");
			return null;
		}
	}

	/** An ISO date, YYYY-MM-DD. */
	public LocalDate date(String column) {
		String value = cell(column);
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			error(column, "Must be a date like 2026-10-31");
			return null;
		}
	}

	/** One of the enum's constants, ignoring case and treating spaces and hyphens as underscores. */
	public <E extends Enum<E>> E choice(String column, Class<E> type) {
		String value = cell(column);
		if (value == null) {
			return null;
		}
		String wanted = value.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
		for (E constant : type.getEnumConstants()) {
			if (constant.name().equals(wanted)) {
				return constant;
			}
		}
		error(column, "Must be one of: " + Arrays.stream(type.getEnumConstants())
			.map(Enum::name)
			.collect(Collectors.joining(", ")));
		return null;
	}

	public String email(String column) {
		String value = text(column, 255);
		if (value != null && !EMAIL.matcher(value).matches()) {
			error(column, "Must be a valid email address");
			return null;
		}
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	/** An absolute http(s) URL, or a site path starting with "/" when {@code allowPath} is set. */
	public String url(String column, int maxLength, boolean allowPath) {
		String value = text(column, maxLength);
		if (value == null) {
			return null;
		}
		if (allowPath && value.startsWith("/") && !value.startsWith("//") && !value.contains(" ")) {
			return value;
		}
		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
				return value;
			}
		}
		catch (URISyntaxException ex) {
			// reported below
		}
		error(column, allowPath ? "Must be a URL (https://…) or a path starting with /" : "Must be a URL (https://…)");
		return null;
	}

	private String cell(String column) {
		CsvColumn definition = columns.get(column);
		if (definition == null) {
			throw new IllegalArgumentException("Unknown column " + column);
		}
		String value = row.get(column);
		if (value == null && definition.required()) {
			error(column, "Required");
		}
		return value;
	}

}
