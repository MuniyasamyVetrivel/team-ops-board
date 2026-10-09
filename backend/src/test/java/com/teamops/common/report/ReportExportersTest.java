package com.teamops.common.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.teamops.common.exception.ApiException;

class ReportExportersTest {

	private final ReportExporters exporters = new ReportExporters(List.of(new CsvReportExporter()));

	private final ReportDocument document = new ReportDocument("Task report", "2026-09-10 to 2026-10-09", "task-report",
			List.of(new ReportDocument.Section("Summary", List.of("Measure", "Value"),
					List.of(List.of("Tasks created", "12"), List.of("=HYPERLINK(\"x\")", "-5"))),
					new ReportDocument.Section("Weekly trend", List.of("Week starting", "Created"), List.of())));

	@Test
	void csvWritesTheTitleThenEachSectionAsATitledTable() {
		ResponseEntity<byte[]> response = exporters.download(document, ReportFormat.CSV);

		String csv = new String(response.getBody(), StandardCharsets.UTF_8);
		assertThat(csv).startsWith("﻿Task report\r\n2026-09-10 to 2026-10-09\r\n\r\nSummary\r\nMeasure,Value\r\n")
			.contains("Tasks created,12\r\n")
			// Formula injection is neutralised; plain negative numbers stay numbers.
			.contains("\"'=HYPERLINK(\"\"x\"\")\",-5\r\n")
			.endsWith("\r\nWeekly trend\r\nWeek starting,Created\r\n");
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment").contains("task-report.csv");
		assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeaders().getContentType()).hasToString("text/csv;charset=UTF-8");
	}

	@Test
	void aFormatWithoutAnExporterIsRefused() {
		assertThat(exporters.supports(ReportFormat.PDF)).isFalse();
		assertThatThrownBy(() -> exporters.download(document, ReportFormat.PDF)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo("FORMAT_NOT_AVAILABLE");
	}

	@Test
	void cellsArePlainSpreadsheetValues() {
		assertThat(Cells.of(new java.math.BigDecimal("80.00"))).isEqualTo("80");
		assertThat(Cells.of(new java.math.BigDecimal("35.42"))).isEqualTo("35.42");
		assertThat(Cells.of((Integer) null)).isEmpty();
		assertThat(Cells.of(true)).isEqualTo("Yes");
	}

}
