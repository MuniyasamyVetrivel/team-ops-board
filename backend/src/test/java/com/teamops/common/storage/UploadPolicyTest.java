package com.teamops.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.teamops.common.exception.ApiException;

class UploadPolicyTest {

	private static final long MAX = 20L * 1024 * 1024;

	@Test
	void acceptsAllowedTypesAndDerivesContentTypeFromTheExtension() {
		UploadPolicy.Checked checked = UploadPolicy.check("Q4 Report.PDF", 1024, MAX);

		assertThat(checked.fileName()).isEqualTo("Q4 Report.PDF");
		assertThat(checked.extension()).isEqualTo("pdf");
		assertThat(checked.contentType()).isEqualTo("application/pdf");
	}

	@ParameterizedTest
	@ValueSource(strings = { "payload.exe", "page.html", "script.js", "noextension", ".bashrc", "archive.tar.gz" })
	void rejectsDisallowedTypes(String name) {
		assertThatThrownBy(() -> UploadPolicy.check(name, 10, MAX)).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isIn("FILE_TYPE_NOT_ALLOWED", "INVALID_FILE_NAME"));
	}

	@Test
	void rejectsEmptyAndOversizedFiles() {
		assertThatThrownBy(() -> UploadPolicy.check("a.pdf", 0, MAX)).hasMessageContaining("empty");
		assertThatThrownBy(() -> UploadPolicy.check("a.pdf", MAX + 1, MAX)).hasMessageContaining("20 MB");
	}

	@Test
	void sanitisesPathsAndReservedCharacters() {
		assertThat(UploadPolicy.sanitise("../../etc/passwd.txt")).isEqualTo("passwd.txt");
		assertThat(UploadPolicy.sanitise("C:\\Users\\me\\notes.txt")).isEqualTo("notes.txt");
		assertThat(UploadPolicy.sanitise("bad<name>:?.png")).isEqualTo("bad_name___.png");
		assertThat(UploadPolicy.sanitise("x".repeat(300) + ".pdf")).hasSize(UploadPolicy.MAX_NAME_LENGTH).endsWith(".pdf");
	}

	@Test
	void localStorageNeverResolvesOutsideItsRoot(@TempDir java.nio.file.Path dir) {
		LocalFileStorageService storage = new LocalFileStorageService(new StorageProperties(dir.toString(), 20));

		assertThat(storage.resolve("2026/10/abc.pdf")).startsWithRaw(dir.toAbsolutePath().normalize());
		assertThatThrownBy(() -> storage.resolve("../outside.pdf")).isInstanceOf(UncheckedIOException.class);
	}

}
