package com.teamops.backup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The chunked AES-256-GCM backup format, and the restore tool (scripts/BackupTool.java) reading it. */
class BackupCryptoTest {

	private static final String KEY = BackupCrypto.newKey();

	private final SecretKey key = BackupCrypto.key(KEY);

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 15, 16, 17, 64, 100 })
	void roundTripsAnySizeAcrossChunkBoundaries(int size) throws IOException {
		byte[] data = random(size);
		byte[] sealed = encrypt(data, 16);
		assertThat(decrypt(sealed, key)).isEqualTo(data);
		assertThat(new String(sealed, 0, 8, StandardCharsets.US_ASCII)).isEqualTo("TOBKUP1\n");
	}

	@Test
	void theSameDataEncryptsDifferentlyEachTime() throws IOException {
		byte[] data = random(50);
		assertThat(encrypt(data, 16)).isNotEqualTo(encrypt(data, 16));
	}

	@Test
	void aWrongKeyIsRejected() throws IOException {
		byte[] sealed = encrypt(random(40), 16);
		assertThatThrownBy(() -> decrypt(sealed, BackupCrypto.key(BackupCrypto.newKey())))
			.hasMessageContaining("wrong key or damaged");
	}

	@Test
	void aChangedByteIsRejected() throws IOException {
		byte[] sealed = encrypt(random(40), 16);
		sealed[sealed.length - 3] ^= 1;
		assertThatThrownBy(() -> decrypt(sealed, key)).isInstanceOf(IOException.class);
	}

	@Test
	void aCutOffFileIsRejectedEvenAtAChunkBoundary() throws IOException {
		byte[] sealed = encrypt(random(48), 16);
		// Header 20 bytes, then three chunks of 1 + 4 + 16 + 16 bytes, the third marked last. Drop it entirely: what
		// is left ends cleanly after a chunk, but no chunk said it was the last.
		int withoutLast = 20 + 2 * (1 + 4 + 16 + 16);
		assertThat(sealed).hasSize(20 + 3 * (1 + 4 + 16 + 16));
		assertThatThrownBy(() -> decrypt(Arrays.copyOf(sealed, withoutLast), key)).hasMessageContaining("truncated");
		assertThatThrownBy(() -> decrypt(Arrays.copyOf(sealed, 30), key)).hasMessageContaining("truncated");
	}

	@Test
	void trailingDataIsRejected() throws IOException {
		byte[] sealed = encrypt(random(10), 16);
		byte[] longer = Arrays.copyOf(sealed, sealed.length + 1);
		assertThatThrownBy(() -> decrypt(longer, key)).hasMessageContaining("after its end");
	}

	@Test
	void keysMustBeBase64Of32Bytes() {
		assertThatThrownBy(() -> BackupCrypto.key(null)).hasMessageContaining("not set");
		assertThatThrownBy(() -> BackupCrypto.key("not base64!")).hasMessageContaining("base64");
		assertThatThrownBy(() -> BackupCrypto.key("c2hvcnQ=")).hasMessageContaining("32 bytes");
		assertThat(BackupCrypto.key(BackupCrypto.newKey()).getEncoded()).hasSize(32);
	}

	/** The restore scripts decrypt with scripts/BackupTool.java: it must read what the app writes. */
	@Test
	void theRestoreToolVerifiesAndExtractsABackup(@TempDir Path dir) throws Exception {
		Path backup = dir.resolve("team-ops-backup-test.tobk");
		try (ZipOutputStream zip = new ZipOutputStream(BackupCrypto.encrypt(Files.newOutputStream(backup), key))) {
			zip.putNextEntry(new ZipEntry("database.sql"));
			zip.write("CREATE TABLE t (id INT);\n".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("uploads/2026/10/a.pdf"));
			zip.write(random(3 * 1024 * 1024));
			zip.closeEntry();
		}
		String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(backup)));
		Files.writeString(dir.resolve("team-ops-backup-test.tobk.sha256"), checksum + "  team-ops-backup-test.tobk\n");

		Path out = dir.resolve("restored");
		Result result = tool(KEY, "extract", backup.toString(), out.toString());
		assertThat(result.exit()).as(result.output()).isZero();
		assertThat(result.output()).contains("Checksum OK", "1 uploaded files");
		assertThat(Files.readString(out.resolve("database.sql"))).contains("CREATE TABLE t");
		assertThat(Files.size(out.resolve("uploads/2026/10/a.pdf"))).isEqualTo(3 * 1024 * 1024);

		Result wrongChecksum = tool(KEY, "verify", backup.toString(), "0".repeat(64));
		assertThat(wrongChecksum.exit()).isNotZero();
		assertThat(wrongChecksum.output()).contains("Checksum mismatch");

		Result wrongKey = tool(BackupCrypto.newKey(), "verify", backup.toString());
		assertThat(wrongKey.exit()).isNotZero();
		assertThat(wrongKey.output()).contains("wrong key");
	}

	private record Result(int exit, String output) {

	}

	private static Result tool(String key, String... args) throws Exception {
		Path script = Path.of("..", "scripts", "BackupTool.java").toAbsolutePath().normalize();
		assertThat(script).exists();
		String java = ProcessHandle.current().info().command().orElse("java");
		String[] command = new String[args.length + 2];
		command[0] = java;
		command[1] = script.toString();
		System.arraycopy(args, 0, command, 2, args.length);
		ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
		builder.environment().put("BACKUP_ENCRYPTION_KEY", key);
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor(2, TimeUnit.MINUTES)).isTrue();
		return new Result(process.exitValue(), output);
	}

	private byte[] encrypt(byte[] data, int chunkSize) throws IOException {
		ByteArrayOutputStream sink = new ByteArrayOutputStream();
		try (OutputStream out = BackupCrypto.encrypt(sink, key, chunkSize)) {
			// Uneven writes, to cross chunk boundaries mid-write.
			for (int i = 0; i < data.length; i += 7) {
				out.write(data, i, Math.min(7, data.length - i));
			}
		}
		return sink.toByteArray();
	}

	private static byte[] decrypt(byte[] sealed, SecretKey key) throws IOException {
		try (InputStream in = BackupCrypto.decrypt(new ByteArrayInputStream(sealed), key)) {
			return in.readAllBytes();
		}
	}

	private static byte[] random(int size) {
		byte[] bytes = new byte[size];
		new Random(size).nextBytes(bytes);
		return bytes;
	}

}
