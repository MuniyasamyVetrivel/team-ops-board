package com.teamops.common.csv;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Writes CSV for download: UTF-8 with a byte-order mark (so Excel reads non-ASCII text correctly), RFC 4180 quoting,
 * and protection against spreadsheet formula injection.
 */
public final class CsvWriter {

	private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private static final Pattern NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");

	private CsvWriter() {
	}

	public static byte[] write(List<String> header, List<List<String>> rows) {
		List<List<String>> records = new ArrayList<>(rows.size() + 1);
		records.add(header);
		records.addAll(rows);
		return writeRecords(records);
	}

	/** Records of any length (e.g. several titled tables one after another, separated by empty records). */
	public static byte[] writeRecords(List<List<String>> records) {
		StringWriter out = new StringWriter();
		try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.RFC4180)) {
			for (List<String> record : records) {
				printer.printRecord(record.stream().map(CsvWriter::safe).toList());
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		byte[] body = out.toString().getBytes(StandardCharsets.UTF_8);
		byte[] result = new byte[BOM.length + body.length];
		System.arraycopy(BOM, 0, result, 0, BOM.length);
		System.arraycopy(body, 0, result, BOM.length, body.length);
		return result;
	}

	/**
	 * A cell a spreadsheet could run as a formula (starting with =, +, -, @, tab or carriage return) gets a leading
	 * apostrophe. Plain numbers such as -5 are left alone.
	 */
	static String safe(String cell) {
		if (cell == null || cell.isEmpty() || NUMBER.matcher(cell).matches()) {
			return cell == null ? "" : cell;
		}
		char first = cell.charAt(0);
		return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
				? "'" + cell : cell;
	}

}
