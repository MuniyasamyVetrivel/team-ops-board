package com.teamops.common.report;

/**
 * Renders a {@link ReportDocument} in one format. Register an implementation as a bean to add a format (a PDF
 * exporter only needs this interface); {@link ReportExporters} picks it by {@link #format()}.
 */
public interface ReportExporter {

	ReportFormat format();

	String contentType();

	String extension();

	byte[] export(ReportDocument document);

}
