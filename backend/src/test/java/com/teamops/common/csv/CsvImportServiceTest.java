package com.teamops.common.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.csv.ImportDtos.ImportPreview;
import com.teamops.common.csv.ImportDtos.ImportResult;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;

/** The validate → preview → commit flow with a fake importer; no database. */
class CsvImportServiceTest {

	private static final AuthenticatedUser EDITOR = new AuthenticatedUser(7L, "arun.kumar@teamops.local",
			"Arun Kumar", 7L, Set.of("EMPLOYEE"), Set.of("MARKETING_VIEW", "SEO_VIEW", "SEO_EDIT"));

	private static final AuthenticatedUser VIEWER = new AuthenticatedUser(8L, "kavya.suresh@teamops.local",
			"Kavya Suresh", 7L, Set.of("EMPLOYEE"), Set.of("MARKETING_VIEW", "SEO_VIEW"));

	private static final String CSV = "keyword,volume\n" + "sap testing,880\n" + "erp testing,abc\n"
			+ "SAP Testing,100\n" + "selenium,\n" + ",5\n";

	private final AuditService auditService = mock(AuditService.class);

	private final List<Keyword> saved = new ArrayList<>();

	private CsvImportService service;

	record Keyword(String keyword, Integer volume) {
	}

	/** Keywords are unique ignoring case; volume is optional. */
	private final CsvImporter<Keyword> importer = new CsvImporter<>() {

		@Override
		public String type() {
			return "keywords";
		}

		@Override
		public String label() {
			return "Keywords";
		}

		@Override
		public String description() {
			return "SEO keywords";
		}

		@Override
		public String permission() {
			return "SEO_EDIT";
		}

		@Override
		public List<CsvColumn> columns() {
			return List.of(CsvColumn.required("keyword", "The search phrase", "sap testing"),
					CsvColumn.optional("volume", "Monthly searches", "880"));
		}

		@Override
		public ImportSession<Keyword> open(AuthenticatedUser actor) {
			return new ImportSession<>() {

				@Override
				public Keyword parse(RowReader row) {
					String keyword = row.text("keyword", 100);
					Integer volume = row.integer("volume", 0, 10_000_000);
					return row.valid() ? new Keyword(keyword, volume) : null;
				}

				@Override
				public String key(Keyword record) {
					return record.keyword().toLowerCase();
				}

				@Override
				public int commit(List<Keyword> records) {
					saved.addAll(records);
					return records.size();
				}

			};
		}

	};

	@BeforeEach
	void setUp() {
		service = new CsvImportService(List.of(importer), auditService);
	}

	private static MockMultipartFile file(String text) {
		return new MockMultipartFile("file", "keywords.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void previewSeparatesValidAndInvalidRowsWithoutSaving() {
		ImportPreview preview = service.preview("keywords", file(CSV), EDITOR);

		assertThat(preview.totalRows()).isEqualTo(5);
		assertThat(preview.validCount()).isEqualTo(2);
		assertThat(preview.invalidCount()).isEqualTo(3);
		assertThat(preview.columns()).containsExactly("keyword", "volume");
		assertThat(preview.validRows()).extracting(ImportDtos.PreviewRow::line).containsExactly(2, 5);
		assertThat(preview.invalidRows()).extracting(ImportDtos.PreviewRow::line).containsExactly(3, 4, 6);
		assertThat(preview.invalidRows().get(0).errors())
			.containsExactly(new ImportDtos.CellError("volume", "Must be a whole number"));
		assertThat(preview.invalidRows().get(1).errors())
			.containsExactly(new ImportDtos.CellError(null, "Duplicate of row 2"));
		assertThat(preview.invalidRows().get(2).errors())
			.containsExactly(new ImportDtos.CellError("keyword", "Required"));
		assertThat(preview.invalidRows().get(0).values()).isEqualTo(Map.of("keyword", "erp testing", "volume", "abc"));
		assertThat(preview.checksum()).hasSize(64);
		assertThat(saved).isEmpty();
		verifyNoInteractions(auditService);
	}

	@Test
	void commitRefusesWhileRowsHaveErrorsUnlessTheyAreSkipped() {
		String checksum = service.preview("keywords", file(CSV), EDITOR).checksum();

		assertThatThrownBy(() -> service.commit("keywords", file(CSV), checksum, false, EDITOR, ClientInfo.unknown()))
			.isInstanceOf(ApiException.class)
			.hasMessageStartingWith("3 rows have errors")
			.extracting("code")
			.isEqualTo("IMPORT_HAS_ERRORS");
		assertThat(saved).isEmpty();

		ImportResult result = service.commit("keywords", file(CSV), checksum, true, EDITOR, ClientInfo.unknown());

		assertThat(result).isEqualTo(new ImportResult("keywords", 2, 3));
		assertThat(saved).containsExactly(new Keyword("sap testing", 880), new Keyword("selenium", null));
		verify(auditService).record(eq(AuditAction.CSV_IMPORTED), eq(7L), eq("IMPORT"), isNull(),
				eq(Map.of("type", "keywords", "fileName", "keywords.csv", "checksum", checksum, "imported", 2,
						"skipped", 3)),
				any());
	}

	@Test
	void commitRequiresTheFileThatWasPreviewed() {
		String checksum = service.preview("keywords", file("keyword\nsap\n"), EDITOR).checksum();

		assertThatThrownBy(() -> service.commit("keywords", file("keyword\nerp\n"), checksum, false, EDITOR,
				ClientInfo.unknown()))
			.extracting("code")
			.isEqualTo("IMPORT_FILE_CHANGED");
		assertThat(service.commit("keywords", file("keyword\nsap\n"), checksum.toUpperCase(), false, EDITOR,
				ClientInfo.unknown())
			.imported()).isEqualTo(1);
	}

	@Test
	void aFileWithNoValidRowsImportsNothing() {
		String text = "keyword,volume\n,1\n";
		String checksum = service.preview("keywords", file(text), EDITOR).checksum();

		assertThatThrownBy(() -> service.commit("keywords", file(text), checksum, true, EDITOR, ClientInfo.unknown()))
			.extracting("code")
			.isEqualTo("NOTHING_TO_IMPORT");
	}

	@Test
	void importersRequireTheirOwnPermission() {
		assertThat(service.definitions(EDITOR)).extracting(ImportDtos.ImportDefinition::type)
			.containsExactly("keywords");
		assertThat(service.definitions(VIEWER)).isEmpty();
		assertThatThrownBy(() -> service.preview("keywords", file(CSV), VIEWER)).extracting("code")
			.isEqualTo("IMPORT_NOT_ALLOWED");
		assertThatThrownBy(() -> service.template("rankings", EDITOR)).extracting("code")
			.isEqualTo("IMPORT_TYPE_NOT_FOUND");
	}

	@Test
	void onlyCsvFilesWithinTheSizeLimitAreRead() {
		MockMultipartFile excel = new MockMultipartFile("file", "keywords.xlsx", "application/octet-stream",
				new byte[] { 1, 2, 3 });
		assertThatThrownBy(() -> service.preview("keywords", excel, EDITOR)).extracting("code")
			.isEqualTo("CSV_REQUIRED");
		MockMultipartFile huge = new MockMultipartFile("file", "keywords.csv", "text/csv",
				new byte[(int) CsvImportService.MAX_BYTES + 1]);
		assertThatThrownBy(() -> service.preview("keywords", huge, EDITOR)).extracting("code")
			.isEqualTo("FILE_TOO_LARGE");
	}

	@Test
	void theTemplateHasTheHeaderAndAnExample() {
		String text = new String(service.template("keywords", EDITOR), StandardCharsets.UTF_8);
		assertThat(text).isEqualTo("﻿keyword,volume\r\nsap testing,880\r\n");
	}

	@Test
	void twoImportersCannotShareAType() {
		assertThatThrownBy(() -> new CsvImportService(List.of(importer, importer), auditService))
			.isInstanceOf(IllegalStateException.class);
	}

}
