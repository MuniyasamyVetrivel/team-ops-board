package com.teamops.recyclebin.service;

import com.teamops.common.persistence.SoftDeletableEntity;

/**
 * Module-specific recycle bin rules for one record type, as a Spring bean. Types without a handler use the defaults:
 * restore always allowed, and a permanent delete removes the row (children go with it through their cascades).
 */
public interface RecycleBinHandler<T extends SoftDeletableEntity> {

	Class<T> type();

	/** The item's name in the bin, when its code, title or name does not say enough; null for the default. */
	default String label(T entity) {
		return null;
	}

	/**
	 * The department whose managers may see and restore the item, when it is not the record's own department or its
	 * owner's; null for the default.
	 */
	default Long departmentOf(T entity) {
		return null;
	}

	/**
	 * Throws an {@code ApiException} (409) when the record cannot come back as it was, for example because a live
	 * record now uses its unique value or the record it belongs to is itself in the bin.
	 */
	default void checkRestore(T entity) {
	}

	/** Cleans up what the row's removal does not: stored files, rows that point at it without a foreign key. */
	default void beforePurge(T entity) {
	}

	/** Cleans up what had to wait until the row was gone (a stored file the row pointed to). */
	default void afterPurge(T entity) {
	}

}
