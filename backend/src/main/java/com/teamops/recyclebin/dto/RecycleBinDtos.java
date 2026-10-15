package com.teamops.recyclebin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RecycleBinDtos {

	private RecycleBinDtos() {
	}

	/** Filters; {@code from}/{@code to} are business days (inclusive) of the deletion. */
	public record Filter(String module, String entityType, Long deletedById, LocalDate from, LocalDate to,
			String search) {

	}

	public record DepartmentRef(Long id, String name) {

	}

	/**
	 * A deleted record. {@code confirmationText} is what a permanent delete must be confirmed with (the record's name).
	 * {@code purgeOn} is the business day it is deleted for good automatically.
	 */
	public record Item(Long id, String entityType, String entityTypeLabel, Long entityId, String label, String module,
			String moduleLabel, DepartmentRef department, Instant deletedAt, UserSummary deletedBy, LocalDate purgeOn,
			String confirmationText, boolean canRestore, boolean canPurge) {

	}

	public record ModuleInfo(String key, String label) {

	}

	public record TypeInfo(String type, String label, String module) {

	}

	/** What the filters offer, and how long items are kept. */
	public record Catalog(List<ModuleInfo> modules, List<TypeInfo> types, List<UserSummary> deletedBy,
			int retentionDays) {

	}

	/** A permanent delete names the item, so the wrong one is never removed by a slip. */
	public record PurgeRequest(@NotBlank(message = "Type the item's name to confirm") @Size(max = 300) String confirm) {

	}

	/** The record that came back, so the client can open it. */
	public record Restored(String entityType, Long entityId, String label) {

	}

}
