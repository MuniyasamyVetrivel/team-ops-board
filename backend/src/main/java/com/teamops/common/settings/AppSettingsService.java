package com.teamops.common.settings;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Typed access to {@code app_settings}. Values are read on each call (the table is tiny), so admin changes apply
 * immediately. A missing or malformed value falls back to the documented default.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppSettingsService {

	public static final String WORKLOAD_WINDOW_DAYS = "workload.windowDays";

	public static final String WORKLOAD_DEFAULT_TASK_HOURS = "workload.defaultTaskHours";

	public static final int DEFAULT_WINDOW_DAYS = 14;

	public static final BigDecimal DEFAULT_TASK_HOURS = new BigDecimal("4");

	public static final String SLA_WARNING_THRESHOLD_PCT = "sla.warningThresholdPct";

	public static final int DEFAULT_SLA_WARNING_PCT = 75;

	public static final String MARKETING_BEHIND_THRESHOLD_PCT = "marketing.target.behindThresholdPct";

	public static final BigDecimal DEFAULT_BEHIND_THRESHOLD_PCT = new BigDecimal("60");

	public static final String DEFAULT_WEEKLY_CAPACITY_HOURS = "users.defaultWeeklyCapacityHours";

	public static final BigDecimal DEFAULT_CAPACITY_HOURS = new BigDecimal("40");

	public static final String UPLOAD_MAX_SIZE_MB = "upload.maxSizeMb";

	public static final String CELEBRATION_REMINDER_DAYS_BEFORE = "celebrations.reminderDaysBefore";

	public static final int DEFAULT_REMINDER_DAYS_BEFORE = 2;

	public static final int MAX_REMINDER_DAYS_BEFORE = 30;

	public static final String RECYCLE_BIN_RETENTION_DAYS = "recycleBin.retentionDays";

	public static final int DEFAULT_RETENTION_DAYS = 90;

	public static final int MAX_RETENTION_DAYS = 3650;

	/** The Finance Tracker's default currency for new licences and expenses (a three-letter code; records may differ). */
	public static final String FINANCE_DEFAULT_CURRENCY = "finance.defaultCurrency";

	public static final String DEFAULT_FINANCE_CURRENCY = "INR";

	private final AppSettingRepository repository;

	/** The currency new licences and expenses start with when none is given (a three-letter code; INR by default). */
	public String financeDefaultCurrency() {
		return repository.findBySettingKey(FINANCE_DEFAULT_CURRENCY)
			.map(setting -> setting.getSettingValue().trim().toUpperCase(Locale.ROOT))
			.filter(value -> value.matches("[A-Z]{3}"))
			.orElse(DEFAULT_FINANCE_CURRENCY);
	}

	/** Days ahead (plus overdue) counted towards workload %. */
	public int workloadWindowDays() {
		int days = decimal(WORKLOAD_WINDOW_DAYS, BigDecimal.valueOf(DEFAULT_WINDOW_DAYS)).intValue();
		return days < 1 ? DEFAULT_WINDOW_DAYS : days;
	}

	/** Hours assumed for an active task that has no estimate. */
	public BigDecimal defaultTaskHours() {
		BigDecimal hours = decimal(WORKLOAD_DEFAULT_TASK_HOURS, DEFAULT_TASK_HOURS);
		return hours.signum() < 0 ? DEFAULT_TASK_HOURS : hours;
	}

	/** Elapsed SLA % at which a ticket shows a warning (1–99). Snapshotted on each ticket at creation. */
	public int slaWarningThresholdPct() {
		int pct = decimal(SLA_WARNING_THRESHOLD_PCT, BigDecimal.valueOf(DEFAULT_SLA_WARNING_PCT)).intValue();
		return pct < 1 || pct > 99 ? DEFAULT_SLA_WARNING_PCT : pct;
	}

	/** Achievement % below which a marketing target is BEHIND (0–100). Target types may override it. */
	public BigDecimal marketingBehindThresholdPct() {
		BigDecimal pct = decimal(MARKETING_BEHIND_THRESHOLD_PCT, DEFAULT_BEHIND_THRESHOLD_PCT);
		return pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0 ? DEFAULT_BEHIND_THRESHOLD_PCT : pct;
	}

	/** Weekly capacity given to a new user when the form leaves it empty (1–80 hours). */
	public BigDecimal defaultWeeklyCapacityHours() {
		BigDecimal hours = decimal(DEFAULT_WEEKLY_CAPACITY_HOURS, DEFAULT_CAPACITY_HOURS);
		return hours.compareTo(BigDecimal.ONE) < 0 || hours.compareTo(BigDecimal.valueOf(80)) > 0
				? DEFAULT_CAPACITY_HOURS : hours;
	}

	/**
	 * Days before a birthday, work anniversary, important day or event that its reminder goes out (0–30; 0 sends only
	 * the on-the-day reminder).
	 */
	public int celebrationReminderDaysBefore() {
		int days = decimal(CELEBRATION_REMINDER_DAYS_BEFORE, BigDecimal.valueOf(DEFAULT_REMINDER_DAYS_BEFORE)).intValue();
		return days < 0 || days > MAX_REMINDER_DAYS_BEFORE ? DEFAULT_REMINDER_DAYS_BEFORE : days;
	}

	/** Days a deleted item stays in the recycle bin before it is permanently deleted automatically (1–3650). */
	public int recycleBinRetentionDays() {
		int days = decimal(RECYCLE_BIN_RETENTION_DAYS, BigDecimal.valueOf(DEFAULT_RETENTION_DAYS)).intValue();
		return days < 1 || days > MAX_RETENTION_DAYS ? DEFAULT_RETENTION_DAYS : days;
	}

	/**
	 * Upload limit in bytes: the admin setting, never above {@code ceilingMb} (the multipart limit the server was
	 * started with, which rejects larger requests before they reach the application).
	 */
	public long uploadMaxBytes(int ceilingMb) {
		int mb = decimal(UPLOAD_MAX_SIZE_MB, BigDecimal.valueOf(ceilingMb)).intValue();
		int effective = mb < 1 ? ceilingMb : Math.min(mb, ceilingMb);
		return effective * 1024L * 1024L;
	}

	private BigDecimal decimal(String key, BigDecimal fallback) {
		return repository.findBySettingKey(key).map(setting -> {
			try {
				return new BigDecimal(setting.getSettingValue().trim());
			}
			catch (NumberFormatException ex) {
				log.warn("Setting {} has a non-numeric value '{}'; using {}", key, setting.getSettingValue(), fallback);
				return fallback;
			}
		}).orElse(fallback);
	}

}
