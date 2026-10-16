package com.teamops.recyclebin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
	 * {@code purgeOn} is the business day it is deleted for good automatically, {@code daysUntilPurge} how many
	 * business days from today that is (0: tonight).
	 */
	public record Item(Long id, String entityType, String entityTypeLabel, Long entityId, String label, String module,
			String moduleLabel, DepartmentRef department, Instant deletedAt, UserSummary deletedBy, LocalDate purgeOn,
			long daysUntilPurge, String confirmationText) {

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

	/**
	 * What restoring an item would run into. {@code blockCode}/{@code blockMessage} name a clash that stops the
	 * restore (null when it can go ahead); {@code warnings} are clashes it survives, such as a work email another
	 * employee now uses.
	 */
	public record RestoreCheck(boolean canRestore, String blockCode, String blockMessage, List<String> warnings) {

	}

	/** Undo a delete just made: the record's type and id, as the delete toast knows them. */
	public record UndoRequest(@NotBlank @Size(max = 40) String entityType, @NotNull Long entityId) {

	}

	/** The record that came back, so the client can open it. */
	public record Restored(String entityType, Long entityId, String label) {

	}

}
