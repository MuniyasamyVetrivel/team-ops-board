package com.teamops.admin.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.admin.dto.SettingDtos.SettingItem;
import com.teamops.admin.dto.SettingDtos.UpdateSetting;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSetting;
import com.teamops.common.settings.AppSettingRepository;
import com.teamops.common.storage.StorageProperties;
import com.teamops.common.web.ClientInfo;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lists and changes the admin settings in {@link SettingDefinition}. Readers such as {@code AppSettingsService} read
 * the table on every call, so a change applies straight away.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsAdminService {

	private final AppSettingRepository settingRepository;

	private final UserRepository userRepository;

	private final StorageProperties storage;

	private final AuditService auditService;

	public List<SettingItem> list() {
		Map<String, AppSetting> byKey = settingRepository.findAll()
			.stream()
			.collect(Collectors.toMap(AppSetting::getSettingKey, Function.identity()));
		Map<Long, User> editors = userRepository
			.findAllById(byKey.values().stream().map(AppSetting::getUpdatedBy).filter(Objects::nonNull).toList())
			.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));
		return Arrays.stream(SettingDefinition.values())
			.filter(d -> byKey.containsKey(d.key()))
			.map(d -> {
				AppSetting setting = byKey.get(d.key());
				User editor = setting.getUpdatedBy() == null ? null : editors.get(setting.getUpdatedBy());
				return SettingItem.of(d, setting, storage.maxFileSizeMb(), UserSummary.of(editor));
			})
			.toList();
	}

	@Transactional
	public SettingItem update(String key, UpdateSetting request, AuthenticatedUser actor, ClientInfo client) {
		SettingDefinition definition = SettingDefinition.forKey(key)
			.orElseThrow(() -> ApiException.notFound("SETTING_NOT_FOUND", "Setting not found"));
		AppSetting setting = settingRepository.findBySettingKey(key)
			.orElseThrow(() -> ApiException.notFound("SETTING_NOT_FOUND", "Setting not found"));
		if (!Objects.equals(setting.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE",
					"Someone else changed this setting just now. Reload and try again.");
		}
		String value = definition.normalize(request.value(), storage.maxFileSizeMb());
		AuditChanges changes = new AuditChanges().track(key, setting.getSettingValue(), value);
		if (!changes.isEmpty()) {
			setting.setSettingValue(value);
			setting.setUpdatedBy(actor.id());
			settingRepository.flush();
			auditService.record(AuditAction.SETTING_UPDATED, actor.id(), "SETTING", setting.getId(),
					changes.toDetails(), client);
		}
		User editor = setting.getUpdatedBy() == null ? null : userRepository.findById(setting.getUpdatedBy()).orElse(null);
		return SettingItem.of(definition, setting, storage.maxFileSizeMb(), UserSummary.of(editor));
	}

}
