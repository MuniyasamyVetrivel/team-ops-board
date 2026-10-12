package com.teamops.common.csv;

import java.util.List;

import com.teamops.common.security.AuthenticatedUser;

/**
 * One importable record type (keywords, rankings, leads, …). Register an implementation as a Spring bean and it is
 * available through the import endpoints; {@link CsvImportService} handles upload checks, parsing, duplicate rows,
 * the preview and the all-or-nothing commit.
 *
 * @param <T> the validated record the importer saves
 */
public interface CsvImporter<T> {

	/** Which import endpoints serve an importer; an importer is only reachable through its own area's endpoints. */
	enum Area {

		/** {@code /api/marketing/imports}: marketing figures, employees and important days. */
		MARKETING,
		/** {@code /api/users/import}: user administration. */
		USERS

	}

	/** Stable identifier used in URLs, e.g. {@code keywords}. */
	String type();

	default Area area() {
		return Area.MARKETING;
	}

	String label();

	String description();

	/** The permission needed to preview and import, e.g. {@code SEO_EDIT}. */
	String permission();

	List<CsvColumn> columns();

	/** Starts a parse for one file, e.g. to load lookups once. Preview and commit each open their own session. */
	ImportSession<T> open(AuthenticatedUser actor);

	interface ImportSession<T> {

		/**
		 * Validates one row. Report problems on the reader ({@link RowReader#error}) and return {@code null} for an
		 * invalid row; a non-null result is only used when the reader has no errors.
		 */
		T parse(RowReader row);

		/** Identity of a record within the file, so a repeated row is reported; {@code null} skips the check. */
		default String key(T record) {
			return null;
		}

		/** Saves the valid records inside the import's transaction and returns how many were saved. */
		int commit(List<T> records);

		/**
		 * A file returned once with the commit's result (e.g. temporary passwords), called after {@link #commit}.
		 * It is never stored, so the importer must not put anything in it that it cannot afford to lose.
		 */
		default ImportDtos.ResultFile resultFile() {
			return null;
		}

	}

}
