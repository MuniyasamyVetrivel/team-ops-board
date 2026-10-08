package com.teamops.common.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.csv.CsvImporter.ImportSession;
import com.teamops.common.csv.CsvReader.CsvTable;
import com.teamops.common.csv.ImportDtos.CellError;
import com.teamops.common.csv.ImportDtos.ImportDefinition;
import com.teamops.common.csv.ImportDtos.ImportPreview;
import com.teamops.common.csv.ImportDtos.ImportResult;
import com.teamops.common.csv.ImportDtos.PreviewRow;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.UploadPolicy;
import com.teamops.common.web.ClientInfo;

/**
 * The CSV import flow (brief section 53): upload → validate every row → preview valid rows, invalid rows and their
 * errors → commit. Nothing is saved by the preview. The commit re-validates the same file (identified by its
 * checksum) in one transaction, never saves an invalid row, and refuses to run while rows have errors unless the
 * user explicitly chose to skip them.
 */
@Service
public class CsvImportService {

	public static final int MAX_ROWS = 5_000;

	public static final long MAX_BYTES = 2L * 1024 * 1024;

	static final int PREVIEW_VALID_ROWS = 100;

	static final int PREVIEW_INVALID_ROWS = 500;

	private final Map<String, CsvImporter<?>> importers = new LinkedHashMap<>();

	private final AuditService auditService;

	/** Importers are optional beans: modules register theirs as they are built. */
	@Autowired
	public CsvImportService(ObjectProvider<CsvImporter<?>> importers, AuditService auditService) {
		this(importers.orderedStream().toList(), auditService);
	}

	CsvImportService(List<CsvImporter<?>> importers, AuditService auditService) {
		this.auditService = auditService;
		for (CsvImporter<?> importer : importers) {
			if (this.importers.put(importer.type(), importer) != null) {
				throw new IllegalStateException("Two CSV importers use the type " + importer.type());
			}
		}
	}

	/** Importers the actor may use. */
	public List<ImportDefinition> definitions(AuthenticatedUser actor) {
		return importers.values()
			.stream()
			.filter(importer -> allowed(importer, actor))
			.sorted(Comparator.comparing(CsvImporter::label))
			.map(CsvImportService::definition)
			.toList();
	}

	/** A header row plus one example row. */
	public byte[] template(String type, AuthenticatedUser actor) {
		CsvImporter<?> importer = importer(type, actor);
		List<String> header = importer.columns().stream().map(CsvColumn::name).toList();
		List<String> example = importer.columns().stream().map(c -> c.example() == null ? "" : c.example()).toList();
		return CsvWriter.write(header, List.of(example));
	}

	@Transactional(readOnly = true)
	public ImportPreview preview(String type, MultipartFile file, AuthenticatedUser actor) {
		CsvImporter<?> importer = importer(type, actor);
		Upload upload = read(file);
		Parsed<?> parsed = parse(importer, upload, actor);
		return new ImportPreview(importer.type(), upload.fileName(), upload.checksum(),
				importer.columns().stream().map(CsvColumn::name).toList(), parsed.table().unknownColumns(),
				parsed.table().rows().size(), parsed.records().size(), parsed.invalidCount(),
				parsed.validRows().subList(0, Math.min(PREVIEW_VALID_ROWS, parsed.validRows().size())),
				parsed.invalidRows().subList(0, Math.min(PREVIEW_INVALID_ROWS, parsed.invalidRows().size())));
	}

	@Transactional
	public ImportResult commit(String type, MultipartFile file, String checksum, boolean skipInvalid,
			AuthenticatedUser actor, ClientInfo client) {
		CsvImporter<?> importer = importer(type, actor);
		Upload upload = read(file);
		if (!upload.checksum().equalsIgnoreCase(checksum == null ? "" : checksum.strip())) {
			throw ApiException.conflict("IMPORT_FILE_CHANGED",
					"This is not the file you previewed. Preview it again before importing.");
		}
		Parsed<?> parsed = parse(importer, upload, actor);
		if (parsed.invalidCount() > 0 && !skipInvalid) {
			throw ApiException.badRequest("IMPORT_HAS_ERRORS", parsed.invalidCount() + " row"
					+ (parsed.invalidCount() == 1 ? " has" : "s have")
					+ " errors. Fix the file, or confirm that those rows should be skipped.");
		}
		if (parsed.records().isEmpty()) {
			throw ApiException.badRequest("NOTHING_TO_IMPORT", "The file has no valid rows to import");
		}
		int imported = parsed.commit();
		auditService.record(AuditAction.CSV_IMPORTED, actor.id(), "IMPORT", null,
				Map.of("type", importer.type(), "fileName", upload.fileName(), "checksum", upload.checksum(),
						"imported", imported, "skipped", parsed.invalidCount()),
				client);
		return new ImportResult(importer.type(), imported, parsed.invalidCount());
	}

	private CsvImporter<?> importer(String type, AuthenticatedUser actor) {
		CsvImporter<?> importer = importers.get(type);
		if (importer == null) {
			throw ApiException.notFound("IMPORT_TYPE_NOT_FOUND", "There is no import for '" + type + "'");
		}
		if (!allowed(importer, actor)) {
			throw ApiException.forbidden("IMPORT_NOT_ALLOWED", "You do not have permission to import " + importer.label());
		}
		return importer;
	}

	private static boolean allowed(CsvImporter<?> importer, AuthenticatedUser actor) {
		return actor.isSuperAdmin() || actor.hasPermission(importer.permission());
	}

	private static ImportDefinition definition(CsvImporter<?> importer) {
		return new ImportDefinition(importer.type(), importer.label(), importer.description(), importer.columns(),
				MAX_ROWS);
	}

	private record Upload(String fileName, byte[] bytes, String checksum) {
	}

	private static Upload read(MultipartFile file) {
		if (file == null) {
			throw ApiException.badRequest("MISSING_FILE", "No file was uploaded");
		}
		UploadPolicy.Checked checked = UploadPolicy.check(file.getOriginalFilename(), file.getSize(), MAX_BYTES);
		if (!checked.extension().equals("csv")) {
			throw ApiException.badRequest("CSV_REQUIRED", "Upload a .csv file. In Excel use Save As → CSV UTF-8.");
		}
		try {
			byte[] bytes = file.getBytes();
			return new Upload(checked.fileName(), bytes, sha256(bytes));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Every row validated: the records to save, and the rows for the preview. */
	private record Parsed<T>(CsvTable table, ImportSession<T> session, List<T> records, List<PreviewRow> validRows,
			List<PreviewRow> invalidRows) {

		int invalidCount() {
			return invalidRows.size();
		}

		int commit() {
			return session.commit(records);
		}

	}

	private static <T> Parsed<T> parse(CsvImporter<T> importer, Upload upload, AuthenticatedUser actor) {
		CsvTable table = CsvReader.read(upload.bytes(), importer.columns(), MAX_ROWS);
		ImportSession<T> session = importer.open(actor);
		Map<String, Integer> seen = new HashMap<>();
		List<T> records = new ArrayList<>();
		List<PreviewRow> valid = new ArrayList<>();
		List<PreviewRow> invalid = new ArrayList<>();
		for (CsvRow row : table.rows()) {
			RowReader reader = new RowReader(row, importer.columns());
			T record = session.parse(reader);
			if (reader.valid() && record != null) {
				String key = session.key(record);
				Integer first = key == null ? null : seen.putIfAbsent(key, row.line());
				if (first != null) {
					reader.error(null, "Duplicate of row " + first);
				}
			}
			else if (reader.valid()) {
				reader.error(null, "The row could not be read");
			}
			List<CellError> errors = reader.errors();
			if (errors.isEmpty()) {
				records.add(record);
				valid.add(new PreviewRow(row.line(), row.values(), List.of()));
			}
			else {
				invalid.add(new PreviewRow(row.line(), row.values(), errors));
			}
		}
		return new Parsed<>(table, session, records, valid, invalid);
	}

}
