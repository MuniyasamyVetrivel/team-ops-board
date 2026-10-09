package com.teamops.common.report;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.teamops.common.exception.ApiException;

/** The registered {@link ReportExporter}s, and the download response every report export returns. */
@Component
public class ReportExporters {

	private final Map<ReportFormat, ReportExporter> exporters = new EnumMap<>(ReportFormat.class);

	public ReportExporters(List<ReportExporter> exporters) {
		exporters.forEach(exporter -> this.exporters.put(exporter.format(), exporter));
	}

	public boolean supports(ReportFormat format) {
		return exporters.containsKey(format);
	}

	/** The rendered report as an attachment ({@code nosniff}); 400 {@code FORMAT_NOT_AVAILABLE} for a format not built yet. */
	public ResponseEntity<byte[]> download(ReportDocument document, ReportFormat format) {
		ReportExporter exporter = exporters.get(format == null ? ReportFormat.CSV : format);
		if (exporter == null) {
			throw ApiException.badRequest("FORMAT_NOT_AVAILABLE", format + " export is not available yet");
		}
		String fileName = document.fileName() + "." + exporter.extension();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(exporter.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(exporter.export(document));
	}

}
