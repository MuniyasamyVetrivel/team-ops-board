package com.teamops.common.settings;

import java.math.BigDecimal;

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

	private final AppSettingRepository repository;

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
