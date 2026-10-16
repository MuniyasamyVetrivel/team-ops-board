package com.teamops.backup.service;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import com.teamops.backup.dto.BackupDtos;

import lombok.RequiredArgsConstructor;

/**
 * {@code /actuator/health} component "backup": {@link #WARNING} when the last successful backup is older than 26
 * hours, the last run failed, or backups cannot run. WARNING still answers HTTP 200 (application.yml), so a load
 * balancer keeps the app in service while monitoring sees the problem.
 */
@Component("backup")
@RequiredArgsConstructor
class BackupHealthIndicator implements HealthIndicator {

	static final Status WARNING = new Status("WARNING", "Backups need attention");

	private final BackupService backups;

	@Override
	public Health health() {
		BackupDtos.Status status = backups.status();
		Health.Builder builder = status.healthy() ? Health.up() : Health.status(WARNING);
		status.problems().forEach(p -> builder.withDetail(p.code(), p.message()));
		if (status.lastSuccess() != null) {
			builder.withDetail("lastSuccessAt", status.lastSuccess().finishedAt());
		}
		return builder.build();
	}

}
