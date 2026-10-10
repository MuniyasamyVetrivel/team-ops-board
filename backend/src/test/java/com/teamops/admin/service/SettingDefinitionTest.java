package com.teamops.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.common.settings.AppSettingsService;

class SettingDefinitionTest {

	private static final int UPLOAD_CEILING = 20;

	@Test
	void everyKeyReadByTheApplicationIsEditable() {
		assertThat(SettingDefinition.forKey(AppSettingsService.WORKLOAD_WINDOW_DAYS)).isPresent();
		assertThat(SettingDefinition.forKey(AppSettingsService.WORKLOAD_DEFAULT_TASK_HOURS)).isPresent();
		assertThat(SettingDefinition.forKey(AppSettingsService.DEFAULT_WEEKLY_CAPACITY_HOURS)).isPresent();
		assertThat(SettingDefinition.forKey(AppSettingsService.SLA_WARNING_THRESHOLD_PCT)).isPresent();
		assertThat(SettingDefinition.forKey(AppSettingsService.MARKETING_BEHIND_THRESHOLD_PCT)).isPresent();
		assertThat(SettingDefinition.forKey(AppSettingsService.UPLOAD_MAX_SIZE_MB)).isPresent();
		assertThat(SettingDefinition.forKey("made.up")).isEmpty();
	}

	@Test
	void valuesAreStoredInCanonicalForm() {
		assertThat(SettingDefinition.WORKLOAD_WINDOW_DAYS.normalize(" 21 ", UPLOAD_CEILING)).isEqualTo("21");
		assertThat(SettingDefinition.WORKLOAD_WINDOW_DAYS.normalize("14.0", UPLOAD_CEILING)).isEqualTo("14");
		assertThat(SettingDefinition.WORKLOAD_DEFAULT_TASK_HOURS.normalize("4.50", UPLOAD_CEILING)).isEqualTo("4.5");
		assertThat(SettingDefinition.MARKETING_BEHIND_THRESHOLD_PCT.normalize("100", UPLOAD_CEILING)).isEqualTo("100");
		assertThat(SettingDefinition.MARKETING_BEHIND_THRESHOLD_PCT.normalize("0", UPLOAD_CEILING)).isEqualTo("0");
	}

	@Test
	void rangesAndTypesAreEnforced() {
		assertInvalid(SettingDefinition.WORKLOAD_WINDOW_DAYS, "0", "between 1 and 90 days");
		assertInvalid(SettingDefinition.WORKLOAD_WINDOW_DAYS, "91", "between 1 and 90 days");
		assertInvalid(SettingDefinition.WORKLOAD_WINDOW_DAYS, "7.5", "whole number");
		assertInvalid(SettingDefinition.SLA_WARNING_THRESHOLD_PCT, "100", "between 1 and 99");
		assertInvalid(SettingDefinition.DEFAULT_WEEKLY_CAPACITY_HOURS, "0.5", "between 1 and 80");
		assertInvalid(SettingDefinition.WORKLOAD_DEFAULT_TASK_HOURS, "4.125", "decimal places");
		assertInvalid(SettingDefinition.MARKETING_BEHIND_THRESHOLD_PCT, "sixty", "must be a number");
		assertInvalid(SettingDefinition.MARKETING_BEHIND_THRESHOLD_PCT, "-1", "between 0 and 100");
	}

	@Test
	void theUploadLimitCannotExceedTheServersMultipartLimit() {
		assertThat(SettingDefinition.UPLOAD_MAX_SIZE_MB.max(UPLOAD_CEILING)).isEqualTo(20);
		assertThat(SettingDefinition.UPLOAD_MAX_SIZE_MB.normalize("20", UPLOAD_CEILING)).isEqualTo("20");
		assertInvalid(SettingDefinition.UPLOAD_MAX_SIZE_MB, "25", "between 1 and 20 MB");
	}

	private static void assertInvalid(SettingDefinition definition, String value, String message) {
		assertThatThrownBy(() -> definition.normalize(value, UPLOAD_CEILING)).isInstanceOf(ApiException.class)
			.hasMessageContaining(message)
			.extracting("code")
			.isEqualTo("INVALID_SETTING");
	}

}
