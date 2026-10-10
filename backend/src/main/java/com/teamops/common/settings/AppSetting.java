package com.teamops.common.settings;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** Admin-editable setting (seeded by V2/V20), edited through the admin settings API (Phase 21). */
@Getter
@Setter
@Entity
@Table(name = "app_settings")
public class AppSetting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "setting_key", nullable = false, length = 100)
	private String settingKey;

	@Column(name = "setting_value", nullable = false, length = 1000)
	private String settingValue;

	@Column(name = "value_type", nullable = false, length = 20)
	private String valueType;

	@Column(name = "description")
	private String description;

	@Column(name = "updated_by")
	private Long updatedBy;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
