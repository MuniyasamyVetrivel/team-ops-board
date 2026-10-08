package com.teamops.common.csv;

import java.util.List;
import java.util.Map;

/** Responses of the CSV import flow: definition → preview (valid, invalid, errors) → commit. */
public final class ImportDtos {

	private ImportDtos() {
	}

	/** What an importer accepts, for the import dialog and the template. */
	public record ImportDefinition(String type, String label, String description, List<CsvColumn> columns,
			int maxRows) {
	}

	/** A problem with one cell; {@code column} is {@code null} when it concerns the whole row. */
	public record CellError(String column, String message) {
	}

	/** A row as uploaded (raw cell text per known column) with its errors, if any. */
	public record PreviewRow(int line, Map<String, String> values, List<CellError> errors) {
	}

	/**
	 * The validation result. Nothing has been saved. {@code checksum} identifies the previewed file and must be sent
	 * back on commit. Only the first rows of each kind are listed; the counts cover the whole file.
	 */
	public record ImportPreview(String type, String fileName, String checksum, List<String> columns,
			List<String> unknownColumns, int totalRows, int validCount, int invalidCount, List<PreviewRow> validRows,
			List<PreviewRow> invalidRows) {
	}

	public record ImportResult(String type, int imported, int skipped) {
	}

}
