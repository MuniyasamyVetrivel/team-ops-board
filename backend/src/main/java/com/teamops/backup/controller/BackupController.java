package com.teamops.backup.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.backup.dto.BackupDtos.Run;
import com.teamops.backup.dto.BackupDtos.Status;
import com.teamops.backup.service.BackupService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Backup status and history (BACKUP_VIEW, Super Admin only by default; 403 otherwise). "Run backup now" is the Super
 * Admin's alone: it starts a backup in the background and answers 202 with the RUNNING run.
 */
@RestController
@PreAuthorize("hasAuthority('BACKUP_VIEW')")
@RequestMapping("/api/admin/backups")
@RequiredArgsConstructor
public class BackupController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("started", List.of("startedAt", "id"));

	private final BackupService backups;

	@GetMapping("/status")
	public Status status() {
		return backups.status();
	}

	@GetMapping
	public PageResponse<Run> history(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "30") int size, @RequestParam(required = false) String sort) {
		return backups.history(PageRequests.of(page, size, sort, SORT_FIELDS, "started,desc"));
	}

	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@PostMapping("/run")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Run runNow(@AuthenticationPrincipal AuthenticatedUser actor) {
		return backups.startNow(actor.id());
	}

}
