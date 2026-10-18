package com.teamops.backup.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.storage.StorageProperties;
import com.teamops.common.web.ClientInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Export all data": one ZIP with every table of the database as {@code tables/<table>.csv} (all rows, read in one
 * consistent snapshot) and every uploaded file under {@code uploads/}, so the data is usable without the app. Rows in
 * the recycle bin are included and marked in an extra {@value #BIN_COLUMN} column. Password and token hashes are
 * replaced by {@value #REDACTED}. Streamed, so the size of the data does not matter. Every export is audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {

	static final String BIN_COLUMN = "_in_recycle_bin";

	static final String REDACTED = "[redacted]";

	/** Secrets that are of no use outside the app and must not leave it. */
	static final Set<String> REDACTED_COLUMNS = Set.of("password_hash", "token_hash");

	private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z0-9_]+");

	private final DataSource dataSource;

	private final StorageProperties storage;

	private final AuditService auditService;

	private final Clock clock;

	private final FinanceRedaction financeRedaction;

	public record Summary(int tables, long rows, int uploadFiles) {

	}

	/**
	 * What an export leaves out for an exporter without FINANCE_VIEW ({@link FinanceRedaction}); {@code active} false
	 * for everyone else.
	 */
	record Redaction(boolean active, FinanceRedaction.Model model, FinanceRedaction.Files files) {

		static final Redaction NONE = new Redaction(false, null, null);

	}

	/**
	 * Writes the ZIP to {@code out} (closed at the end) and audits the export, also when it breaks off. Without
	 * {@code canSeeFinance} (FINANCE_VIEW) finance data stays out of it ({@link FinanceRedaction}).
	 */
	public Summary export(OutputStream out, Long actorId, boolean canSeeFinance, ClientInfo client) throws IOException {
		Map<String, Long> counts = new LinkedHashMap<>();
		int files = 0;
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			Redaction redaction = exportTables(zip, counts, canSeeFinance);
			files = UploadsArchive.addTo(zip, storage.dir(),
					redaction.active() ? redaction.files().storageKeys() : Set.of());
			writeIndex(zip, counts, files, redaction.active());
		}
		catch (IOException | SQLException | RuntimeException ex) {
			audit(actorId, client, counts, files, false, BackupService.rootMessage(ex));
			if (ex instanceof IOException io) {
				throw io;
			}
			throw new IOException("The export failed: " + BackupService.rootMessage(ex), ex);
		}
		Summary summary = new Summary(counts.size(), counts.values().stream().mapToLong(Long::longValue).sum(), files);
		audit(actorId, client, counts, files, true, null);
		return summary;
	}

	private Redaction exportTables(ZipOutputStream zip, Map<String, Long> counts, boolean canSeeFinance)
			throws SQLException, IOException {
		try (Connection connection = dataSource.getConnection()) {
			boolean autoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			try {
				try (Statement snapshot = connection.createStatement()) {
					// One snapshot for every table, so the CSV files agree with each other.
					snapshot.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
				}
				Redaction redaction = canSeeFinance ? Redaction.NONE
						: new Redaction(true, financeRedaction.model(), financeRedaction.financeFiles(connection));
				for (String table : tables(connection)) {
					if (redaction.active() && redaction.model().tables().contains(table.toLowerCase(Locale.ROOT))) {
						continue;
					}
					zip.putNextEntry(new ZipEntry("tables/" + table + ".csv"));
					counts.put(table, exportTable(connection, table, zip, redaction));
					zip.closeEntry();
				}
				return redaction;
			}
			finally {
				connection.rollback();
				connection.setAutoCommit(autoCommit);
			}
		}
	}

	private static List<String> tables(Connection connection) throws SQLException {
		List<String> tables = new ArrayList<>();
		try (PreparedStatement query = connection.prepareStatement(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
						+ "AND table_type = 'BASE TABLE' ORDER BY table_name");
				ResultSet rs = query.executeQuery()) {
			while (rs.next()) {
				String name = rs.getString(1);
				if (TABLE_NAME.matcher(name).matches()) {
					tables.add(name);
				}
			}
		}
		return tables;
	}

	private static long exportTable(Connection connection, String table, OutputStream out, Redaction redaction)
			throws SQLException, IOException {
		String lower = table.toLowerCase(Locale.ROOT);
		Set<String> moneyColumns = redaction.active() ? redaction.model().columns().getOrDefault(lower, Set.of()) : Set.of();
		long rows = 0;
		try (Statement query = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
			// Connector/J streams rows one by one with this fetch size instead of loading the table into memory.
			query.setFetchSize(Integer.MIN_VALUE);
			try (ResultSet rs = query.executeQuery("SELECT * FROM `" + table + "`");
					CsvWriter.Streaming csv = CsvWriter.stream(new NonClosingOutputStream(out))) {
				ResultSetMetaData meta = rs.getMetaData();
				int columns = meta.getColumnCount();
				List<String> header = new ArrayList<>();
				int deletedAt = -1;
				int id = -1;
				int entityType = -1;
				int parentType = -1;
				int financeFlag = -1;
				for (int i = 1; i <= columns; i++) {
					String name = meta.getColumnLabel(i);
					header.add(name);
					switch (name.toLowerCase(Locale.ROOT)) {
						case "deleted_at" -> deletedAt = i;
						case "id" -> id = i;
						case "entity_type" -> entityType = i;
						case "parent_type" -> parentType = i;
						case "finance" -> financeFlag = i;
						default -> {
						}
					}
				}
				if (deletedAt > 0) {
					header.add(BIN_COLUMN);
				}
				csv.row(header);
				while (rs.next()) {
					// Without FINANCE_VIEW: receipts' file rows are left out, as their files are.
					if (redaction.active() && "files".equals(lower) && id > 0
							&& redaction.files().ids().contains(rs.getLong(id))) {
						continue;
					}
					// Rows about a finance record, or about a record filed under one (a receipt's audit entries).
					boolean aboutFinance = redaction.active()
							&& (isFinanceType(redaction, rs, entityType) || isFinanceType(redaction, rs, parentType));
					boolean financeDetails = redaction.active() && financeFlag > 0 && rs.getBoolean(financeFlag);
					List<String> row = new ArrayList<>(header.size());
					for (int i = 1; i <= columns; i++) {
						String name = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
						boolean hide = moneyColumns.contains(name)
								|| (aboutFinance && FinanceRedaction.RECORD_TEXT_COLUMNS.contains(name))
								|| (financeDetails && "details".equals(name));
						row.add(hide ? (rs.getObject(i) == null ? "" : FinanceRedaction.HIDDEN) : cell(rs, meta, i));
					}
					if (deletedAt > 0) {
						row.add(rs.getObject(deletedAt) == null ? "no" : "yes");
					}
					csv.row(row);
					rows++;
				}
			}
		}
		return rows;
	}

	/** Whether the column (if the table has it) names a finance record type. */
	private static boolean isFinanceType(Redaction redaction, ResultSet rs, int column) throws SQLException {
		String type = column > 0 ? rs.getString(column) : null;
		return type != null && redaction.model().recordTypes().contains(type);
	}

	private static String cell(ResultSet rs, ResultSetMetaData meta, int column) throws SQLException {
		if (REDACTED_COLUMNS.contains(meta.getColumnLabel(column).toLowerCase())) {
			return rs.getObject(column) == null ? "" : REDACTED;
		}
		return switch (meta.getColumnType(column)) {
			case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
				byte[] bytes = rs.getBytes(column);
				yield bytes == null ? "" : "base64:" + Base64.getEncoder().encodeToString(bytes);
			}
			default -> {
				String value = rs.getString(column);
				yield value == null ? "" : value;
			}
		};
	}

	private void writeIndex(ZipOutputStream zip, Map<String, Long> counts, int files, boolean financeHidden)
			throws IOException {
		zip.putNextEntry(new ZipEntry("tables.csv"));
		try (CsvWriter.Streaming csv = CsvWriter.stream(new NonClosingOutputStream(zip))) {
			csv.row(List.of("table", "rows"));
			for (Map.Entry<String, Long> entry : counts.entrySet()) {
				csv.row(List.of(entry.getKey(), String.valueOf(entry.getValue())));
			}
		}
		zip.closeEntry();
		zip.putNextEntry(new ZipEntry("README.txt"));
		zip.write(readme(counts.size(), files, financeHidden).getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private String readme(int tables, int files, boolean financeHidden) {
		String finance = !financeHidden ? "" : """
				- Finance data is left out, because the person who exported does not have FINANCE_VIEW: the Finance
				  Tracker's tables and receipts, money amounts elsewhere and the details of records about finance read
				  %s.
				""".formatted(FinanceRedaction.HIDDEN);
		return """
				Team Ops Board - export of all data
				Exported at %s (UTC)

				tables/<table>.csv   every row of each of the %d database tables (UTF-8 CSV, first line = column names)
				tables.csv           the number of rows in each table
				uploads/             the %d uploaded files, in the app's storage layout (the files table maps
				                     storage_key to the original file name)

				Notes
				- Times are UTC.
				- Records in the recycle bin are included; their %s column says "yes".
				- Password and token hashes are replaced by %s.
				- Binary values are written as base64:<data>.
				- Cells starting with =, +, - or @ (other than numbers) get a leading apostrophe so spreadsheets do not
				  run them as formulas.
				""".formatted(clock.instant(), tables, files, BIN_COLUMN, REDACTED) + finance;
	}

	private void audit(Long actorId, ClientInfo client, Map<String, Long> counts, int files, boolean completed,
			String error) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("completed", completed);
		details.put("tables", counts.size());
		details.put("rows", counts.values().stream().mapToLong(Long::longValue).sum());
		details.put("uploadFiles", files);
		if (error != null) {
			details.put("error", error.length() > 1000 ? error.substring(0, 1000) : error);
		}
		try {
			auditService.record(AuditAction.DATA_EXPORTED, actorId, BackupService.ENTITY_TYPE, null, details, client);
		}
		catch (RuntimeException ex) {
			log.error("Could not audit the data export", ex);
		}
	}

}
