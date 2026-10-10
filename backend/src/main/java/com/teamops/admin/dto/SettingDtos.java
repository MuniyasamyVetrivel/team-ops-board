package com.teamops.admin.dto;

import java.time.Instant;

import com.teamops.admin.service.SettingDefinition;
import com.teamops.common.settings.AppSetting;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SettingDtos {

	private SettingDtos() {
	}

	public record SettingItem(String key, String group, String label, String description,
			SettingDefinition.ValueType valueType, String value, String unit, int min, int max, UserSummary updatedBy,
			Instant updatedAt, Integer version) {

		public static SettingItem of(SettingDefinition definition, AppSetting setting, int uploadCeilingMb,
				UserSummary updatedBy) {
			return new SettingItem(definition.key(), definition.group(), definition.label(), setting.getDescription(),
					definition.type(), setting.getSettingValue(), definition.unit(), definition.min(),
					definition.max(uploadCeilingMb), updatedBy, setting.getUpdatedAt(), setting.getVersion());
		}

	}

	public record UpdateSetting(@NotNull(message = "Version is required") Integer version,
			@NotBlank(message = "Value is required") @Size(max = 30, message = "Value is too long") String value) {
	}

}
