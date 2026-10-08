package com.teamops.common.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.teamops.common.csv.CsvReader.CsvTable;
import com.teamops.common.exception.ApiException;

/** Reading uploads and writing downloads. */
class CsvFormatTest {

	private static final List<CsvColumn> COLUMNS = List.of(CsvColumn.required("keyword", "", "sap testing"),
			CsvColumn.required("page_url", "", "/services/sap"), CsvColumn.optional("search_volume", "", "880"));

	private static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void readsRowsByColumnNameWithQuotingBomAndFlexibleHeaders() {
		String csv = "﻿Keyword,Page URL,Search Volume,Notes\r\n" + "\"sap, testing\",/services/sap,880,x\r\n"
				+ ",,,\r\n" + "\"multi\nline\",/blog,,\r\n";

		CsvTable table = CsvReader.read(bytes(csv), COLUMNS, 10);

		assertThat(table.unknownColumns()).containsExactly("Notes");
		assertThat(table.rows()).hasSize(2);
		assertThat(table.rows().get(0).line()).isEqualTo(2);
		assertThat(table.rows().get(0).get("keyword")).isEqualTo("sap, testing");
		assertThat(table.rows().get(0).get("search_volume")).isEqualTo("880");
		// The blank row is skipped but still counted, so line numbers match the spreadsheet.
		assertThat(table.rows().get(1).line()).isEqualTo(4);
		assertThat(table.rows().get(1).get("keyword")).isEqualTo("multi\nline");
		assertThat(table.rows().get(1).get("search_volume")).isNull();
	}

	@Test
	void rejectsFilesWithoutTheRequiredColumns() {
		assertThatThrownBy(() -> CsvReader.read(bytes("keyword,volume\nsap,1\n"), COLUMNS, 10))
			.isInstanceOf(ApiException.class)
			.hasMessage("Missing required column: page_url")
			.extracting("code")
			.isEqualTo("CSV_MISSING_COLUMNS");
		assertThatThrownBy(() -> CsvReader.read(bytes(""), COLUMNS, 10)).extracting("code").isEqualTo("CSV_EMPTY");
		assertThatThrownBy(() -> CsvReader.read(bytes("keyword,page_url,KEYWORD\n"), COLUMNS, 10))
			.extracting("code")
			.isEqualTo("CSV_DUPLICATE_COLUMN");
	}

	@Test
	void rejectsOversizedMalformedAndNonUtf8Files() {
		assertThatThrownBy(() -> CsvReader.read(bytes("keyword,page_url\na,/a\nb,/b\nc,/c\n"), COLUMNS, 2))
			.extracting("code")
			.isEqualTo("CSV_TOO_MANY_ROWS");
		assertThatThrownBy(() -> CsvReader.read(bytes("keyword,page_url\n\"open,/a\n"), COLUMNS, 10))
			.extracting("code")
			.isEqualTo("CSV_MALFORMED");
		byte[] latin1 = "keyword,page_url\ncafé,/a\n".getBytes(StandardCharsets.ISO_8859_1);
		assertThatThrownBy(() -> CsvReader.read(latin1, COLUMNS, 10)).extracting("code").isEqualTo("CSV_ENCODING");
	}

	@Test
	void writesUtf8WithBomQuotingAndFormulaProtection() {
		byte[] out = CsvWriter.write(List.of("name", "value"),
				List.of(List.of("a, b", "-5"), List.of("=HYPERLINK(\"x\")", "@SUM(A1)"), List.of("café", "")));
		assertThat(out[0]).isEqualTo((byte) 0xEF);
		String text = new String(out, 3, out.length - 3, StandardCharsets.UTF_8);
		assertThat(text).isEqualTo("name,value\r\n\"a, b\",-5\r\n\"'=HYPERLINK(\"\"x\"\")\",'@SUM(A1)\r\ncafé,\r\n");
	}

	@Test
	void rowReaderCollectsEveryProblem() {
		List<CsvColumn> columns = List.of(CsvColumn.required("name", "", ""), CsvColumn.optional("position", "", ""),
				CsvColumn.optional("spend", "", ""), CsvColumn.optional("date", "", ""),
				CsvColumn.optional("email", "", ""), CsvColumn.optional("url", "", ""),
				CsvColumn.optional("device", "", ""));
		RowReader row = new RowReader(new CsvRow(5, Map.of("name", " ", "position", "101", "spend",
				"12.345", "date", "31/10/2026", "email", "nope", "url", "javascript:alert(1)", "device", "tablet")),
				columns);

		assertThat(row.text("name", 10)).isNull();
		assertThat(row.integer("position", 1, 100)).isNull();
		assertThat(row.amount("spend", 2)).isNull();
		assertThat(row.date("date")).isNull();
		assertThat(row.email("email")).isNull();
		assertThat(row.url("url", 500, true)).isNull();
		assertThat(row.choice("device", Device.class)).isNull();
		assertThat(row.valid()).isFalse();
		assertThat(row.errors()).extracting(ImportDtos.CellError::column)
			.containsExactly("name", "position", "spend", "date", "email", "url", "device");
		assertThat(row.errors().get(6).message()).isEqualTo("Must be one of: DESKTOP, MOBILE");
	}

	@Test
	void rowReaderParsesValidValues() {
		List<CsvColumn> columns = List.of(CsvColumn.required("volume", "", ""), CsvColumn.required("spend", "", ""),
				CsvColumn.required("date", "", ""), CsvColumn.required("device", "", ""),
				CsvColumn.required("url", "", ""), CsvColumn.required("email", "", ""));
		RowReader row = new RowReader(new CsvRow(2, Map.of("volume", "1,200", "spend", "42,000.50", "date",
				"2026-10-31", "device", "mobile", "url", "/services/sap-testing", "email", "Lead@Example.com")),
				columns);

		assertThat(row.integer("volume", 0, 10_000_000)).isEqualTo(1200);
		assertThat(row.amount("spend", 2)).isEqualByComparingTo("42000.50");
		assertThat(row.date("date")).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(row.choice("device", Device.class)).isEqualTo(Device.MOBILE);
		assertThat(row.url("url", 500, true)).isEqualTo("/services/sap-testing");
		assertThat(row.email("email")).isEqualTo("lead@example.com");
		assertThat(row.valid()).isTrue();
	}

	enum Device {

		DESKTOP, MOBILE

	}

}
