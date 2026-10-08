package com.teamops.common.csv;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.teamops.common.exception.ApiException;

/**
 * Reads an uploaded CSV (UTF-8, optional BOM, comma separated, RFC 4180 quoting) into rows keyed by the importer's
 * column names. File-level problems (no header, missing required columns, too many rows) fail the whole file with a
 * 400; row-level problems are reported by the importer per row.
 */
public final class CsvReader {

	private static final CSVFormat FORMAT = CSVFormat.RFC4180;

	private CsvReader() {
	}

	/** The header and the non-blank data rows, in file order. Unknown columns are ignored. */
	public record CsvTable(List<String> unknownColumns, List<CsvRow> rows) {
	}

	public static CsvTable read(byte[] bytes, List<CsvColumn> columns, int maxRows) {
		String text = decode(bytes);
		List<CSVRecord> records;
		try (CSVParser parser = CSVParser.parse(new StringReader(text), FORMAT)) {
			records = parser.getRecords();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (RuntimeException ex) {
			throw ApiException.badRequest("CSV_MALFORMED", "The file is not valid CSV. Check for unclosed quotes.");
		}
		if (records.isEmpty()) {
			throw ApiException.badRequest("CSV_EMPTY", "The file is empty. Download the template to see the columns.");
		}

		Map<String, CsvColumn> known = new HashMap<>();
		columns.forEach(column -> known.put(CsvColumn.normalise(column.name()), column));
		CSVRecord header = records.get(0);
		Map<Integer, String> positions = new LinkedHashMap<>();
		List<String> unknown = new ArrayList<>();
		for (int i = 0; i < header.size(); i++) {
			String cell = header.get(i);
			CsvColumn column = known.get(CsvColumn.normalise(cell));
			if (column == null) {
				if (!cell.isBlank()) {
					unknown.add(cell.strip());
				}
			}
			else if (positions.containsValue(column.name())) {
				throw ApiException.badRequest("CSV_DUPLICATE_COLUMN", "The column '" + column.name() + "' appears twice");
			}
			else {
				positions.put(i, column.name());
			}
		}
		List<String> missing = columns.stream()
			.filter(CsvColumn::required)
			.map(CsvColumn::name)
			.filter(name -> !positions.containsValue(name))
			.toList();
		if (!missing.isEmpty()) {
			throw ApiException.badRequest("CSV_MISSING_COLUMNS",
					"Missing required column" + (missing.size() > 1 ? "s" : "") + ": " + String.join(", ", missing));
		}

		List<CsvRow> rows = new ArrayList<>();
		for (CSVRecord record : records.subList(1, records.size())) {
			Map<String, String> values = new LinkedHashMap<>();
			boolean blank = true;
			for (Map.Entry<Integer, String> position : positions.entrySet()) {
				String value = position.getKey() < record.size() ? record.get(position.getKey()) : "";
				values.put(position.getValue(), value);
				blank &= value.isBlank();
			}
			if (blank) {
				continue;
			}
			if (rows.size() == maxRows) {
				throw ApiException.badRequest("CSV_TOO_MANY_ROWS",
						"A file can hold at most " + maxRows + " rows. Split it into smaller files.");
			}
			// Line numbers as a spreadsheet shows them: the header is line 1.
			rows.add(new CsvRow((int) record.getRecordNumber(), values));
		}
		return new CsvTable(List.copyOf(unknown), List.copyOf(rows));
	}

	private static String decode(byte[] bytes) {
		try {
			String text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
			return text.startsWith("﻿") ? text.substring(1) : text;
		}
		catch (CharacterCodingException ex) {
			throw ApiException.badRequest("CSV_ENCODING", "Save the file as CSV UTF-8 and upload it again.");
		}
	}

}
