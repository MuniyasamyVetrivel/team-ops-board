package com.teamops.admin.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import com.teamops.common.exception.ApiException;
import com.teamops.common.settings.AppSettingsService;

/**
 * The settings an administrator may change, with the range each one accepts. A key that is not listed here cannot be
 * edited through the API, so a typo can never create a setting nothing reads.
 */
public enum SettingDefinition {

	WORKLOAD_WINDOW_DAYS(AppSettingsService.WORKLOAD_WINDOW_DAYS, "Workload", "Workload window", "days", ValueType.INTEGER,
			1, 90),

	WORKLOAD_DEFAULT_TASK_HOURS(AppSettingsService.WORKLOAD_DEFAULT_TASK_HOURS, "Workload",
			"Hours for a task without an estimate", "hours", ValueType.DECIMAL, 0, 40),

	DEFAULT_WEEKLY_CAPACITY_HOURS(AppSettingsService.DEFAULT_WEEKLY_CAPACITY_HOURS, "People",
			"Default weekly capacity", "hours", ValueType.DECIMAL, 1, 80),

	SLA_WARNING_THRESHOLD_PCT(AppSettingsService.SLA_WARNING_THRESHOLD_PCT, "Help desk", "SLA warning threshold", "%",
			ValueType.INTEGER, 1, 99),

	MARKETING_BEHIND_THRESHOLD_PCT(AppSettingsService.MARKETING_BEHIND_THRESHOLD_PCT, "Digital Marketing",
			"Target behind threshold", "%", ValueType.DECIMAL, 0, 100),

	/** The maximum is the multipart limit the server runs with (see {@link #max(int)}). */
	UPLOAD_MAX_SIZE_MB(AppSettingsService.UPLOAD_MAX_SIZE_MB, "Files", "Maximum upload size", "MB", ValueType.INTEGER, 1,
			-1);

	public enum ValueType {

		INTEGER, DECIMAL

	}

	/** Decimal settings keep at most two decimal places. */
	private static final int MAX_SCALE = 2;

	private final String key;

	private final String group;

	private final String label;

	private final String unit;

	private final ValueType type;

	private final int min;

	private final int max;

	SettingDefinition(String key, String group, String label, String unit, ValueType type, int min, int max) {
		this.key = key;
		this.group = group;
		this.label = label;
		this.unit = unit;
		this.type = type;
		this.min = min;
		this.max = max;
	}

	public static Optional<SettingDefinition> forKey(String key) {
		return Arrays.stream(values()).filter(d -> d.key.equals(key)).findFirst();
	}

	public String key() {
		return key;
	}

	public String group() {
		return group;
	}

	public String label() {
		return label;
	}

	public String unit() {
		return unit;
	}

	public ValueType type() {
		return type;
	}

	public int min() {
		return min;
	}

	/** @param uploadCeilingMb the server's multipart limit, the upper bound of the upload setting */
	public int max(int uploadCeilingMb) {
		return max < 0 ? uploadCeilingMb : max;
	}

	/**
	 * Parses and range-checks {@code raw}, returning the value to store in its canonical form ({@code "14"},
	 * {@code "4.5"}).
	 */
	public String normalize(String raw, int uploadCeilingMb) {
		BigDecimal value;
		try {
			value = new BigDecimal(raw == null ? "" : raw.trim());
		}
		catch (NumberFormatException ex) {
			throw invalid(label + " must be a number");
		}
		value = value.stripTrailingZeros();
		if (value.scale() < 0) {
			value = value.setScale(0);
		}
		if (type == ValueType.INTEGER && value.scale() > 0) {
			throw invalid(label + " must be a whole number");
		}
		if (value.scale() > MAX_SCALE) {
			throw invalid(label + " can have at most " + MAX_SCALE + " decimal places");
		}
		int upper = max(uploadCeilingMb);
		if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(upper)) > 0) {
			throw invalid(label + " must be between " + min + " and " + upper + " " + unit);
		}
		return value.toPlainString();
	}

	private static ApiException invalid(String message) {
		return ApiException.badRequest("INVALID_SETTING", message);
	}

}
