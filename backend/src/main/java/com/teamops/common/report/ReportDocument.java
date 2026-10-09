package com.teamops.common.report;

import java.util.List;

/**
 * A report as neutral, already formatted tables: what every {@link ReportExporter} renders (CSV now, PDF later).
 * Services build it from their DTOs, so a new format never needs to know about a report's domain.
 *
 * @param title e.g. "Digital Marketing monthly report"
 * @param subtitle e.g. "October 2026 against September 2026 · all owners"
 * @param fileName the download's base name without extension, e.g. "marketing-report-2026-10"
 */
public record ReportDocument(String title, String subtitle, String fileName, List<Section> sections) {

	/** One table: a title, its column headers and rows of display text (empty cells are ""). */
	public record Section(String title, List<String> headers, List<List<String>> rows) {

	}

}
