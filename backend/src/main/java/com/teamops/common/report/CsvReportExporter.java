package com.teamops.common.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.teamops.common.csv.CsvWriter;

/**
 * CSV: the title and subtitle, then each section as a titled table, separated by an empty line. Written by
 * {@link CsvWriter} (UTF-8 with BOM, formula-injection safe).
 */
@Component
public class CsvReportExporter implements ReportExporter {

	@Override
	public ReportFormat format() {
		return ReportFormat.CSV;
	}

	@Override
	public String contentType() {
		return "text/csv;charset=UTF-8";
	}

	@Override
	public String extension() {
		return "csv";
	}

	@Override
	public byte[] export(ReportDocument document) {
		List<List<String>> records = new ArrayList<>();
		records.add(List.of(document.title()));
		if (document.subtitle() != null) {
			records.add(List.of(document.subtitle()));
		}
		for (ReportDocument.Section section : document.sections()) {
			records.add(List.of());
			records.add(List.of(section.title()));
			records.add(section.headers());
			records.addAll(section.rows());
		}
		return CsvWriter.writeRecords(records);
	}

}
