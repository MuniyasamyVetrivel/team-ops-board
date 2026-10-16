package com.teamops.backup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.SyncTaskExecutor;

import com.teamops.backup.dto.BackupDtos.Run;
import com.teamops.backup.entity.BackupRun;
import com.teamops.backup.repository.BackupRunRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.storage.StorageProperties;

import tools.jackson.databind.json.JsonMapper;

/** The backup pipeline end to end, with a fake dump and a temporary folder as the target (no database). */
class BackupServiceTest {

	private static final String KEY = BackupCrypto.newKey();

	private static final Instant NOW = Instant.parse("2026-10-16T20:30:00Z");

	@TempDir
	Path temp;

	private Path targetDir;

	private Path workDir;

	private final Map<Long, BackupRun> rows = new HashMap<>();

	private final AtomicLong ids = new AtomicLong();

	private final BackupRunRepository repository = mock(BackupRunRepository.class);

	private final DatabaseDumper dumper = mock(DatabaseDumper.class);

	private final AuditService audit = mock(AuditService.class);

	@BeforeEach
	void setUp() throws IOException {
		targetDir = temp.resolve("backups");
		workDir = temp.resolve("work");
		Path uploads = temp.resolve("uploads/2026/10");
		Files.createDirectories(uploads);
		Files.writeString(uploads.resolve("report.pdf"), "pdf bytes");

		when(repository.save(any())).thenAnswer(inv -> {
			BackupRun run = inv.getArgument(0);
			if (run.getId() == null) {
				run.setId(ids.incrementAndGet());
			}
			rows.put(run.getId(), run);
			return run;
		});
		when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<Long>getArgument(0))));
		when(repository.findByStatusAndTargetAndRemovedAtIsNull(eq(BackupRun.Status.SUCCESS), any()))
			.thenAnswer(inv -> rows.values()
				.stream()
				.filter(r -> r.getStatus() == BackupRun.Status.SUCCESS && r.getRemovedAt() == null
						&& r.getTarget().equals(inv.getArgument(1)))
				.toList());
		when(dumper.database()).thenReturn("team_ops_board");
		doAnswer(inv -> {
			inv.<OutputStream>getArgument(0).write("CREATE TABLE `tasks` (id BIGINT);\n".getBytes(StandardCharsets.UTF_8));
			return null;
		}).when(dumper).dump(any());
	}

	@Test
	void aBackupIsEncryptedStoredWithItsChecksumAndVerified() throws Exception {
		Run run = service(KEY, 30, 12).runNow(BackupRun.Trigger.MANUAL, 7L);

		assertThat(run.status()).isEqualTo("SUCCESS");
		assertThat(run.fileName()).isEqualTo("team-ops-backup-20261016T203000Z.tobk");
		assertThat(run.target()).isEqualTo("LOCAL " + targetDir.toAbsolutePath().normalize());
		assertThat(run.uploadFiles()).isEqualTo(1);
		assertThat(run.finishedAt()).isEqualTo(NOW);
		Path file = targetDir.resolve(run.fileName());
		assertThat(run.sizeBytes()).isEqualTo(Files.size(file));
		assertThat(run.checksum()).isEqualTo(sha256(file));
		assertThat(Files.readString(targetDir.resolve(run.fileName() + ".sha256")))
			.isEqualTo(run.checksum() + "  " + run.fileName() + "\n");

		// Not readable without the key; with it, the dump, the uploads and the manifest.
		assertThat(Files.readString(file, StandardCharsets.ISO_8859_1)).doesNotContain("CREATE TABLE");
		Map<String, String> entries = unzip(file);
		assertThat(entries.keySet()).containsExactly("database.sql", "uploads/2026/10/report.pdf", "manifest.json");
		assertThat(entries.get("database.sql")).contains("CREATE TABLE `tasks`");
		assertThat(entries.get("manifest.json")).contains("\"database\":\"team_ops_board\"", "\"uploadFiles\":1");

		verify(audit).record(eq(AuditAction.BACKUP_COMPLETED), eq(7L), eq("BACKUP"), eq(run.id()),
				argThat(d -> run.checksum().equals(d.get("checksum")) && "MANUAL".equals(d.get("trigger"))), any());
		assertThat(workDirFiles()).isEmpty();
	}

	@Test
	void aFailedDumpIsRecordedAndLeavesNothingBehind() throws Exception {
		doThrow(new IOException("mysqldump failed (exit 2): Access denied for user 'teamops'")).when(dumper).dump(any());

		Run run = service(KEY, 30, 12).runNow(BackupRun.Trigger.SCHEDULED, null);

		assertThat(run.status()).isEqualTo("FAILED");
		assertThat(run.errorMessage()).isEqualTo("mysqldump failed (exit 2): Access denied for user 'teamops'");
		assertThat(run.fileName()).isNull();
		verify(audit).record(eq(AuditAction.BACKUP_FAILED), isNull(), eq("BACKUP"), eq(run.id()),
				argThat(d -> d.get("error").toString().contains("Access denied")), any());
		assertThat(Files.exists(targetDir) ? Files.list(targetDir).toList() : List.of()).isEmpty();
		assertThat(workDirFiles()).isEmpty();
	}

	@Test
	void withoutAKeyEveryRunFails() throws Exception {
		Run run = service("", 30, 12).runNow(BackupRun.Trigger.SCHEDULED, null);
		assertThat(run.status()).isEqualTo("FAILED");
		assertThat(run.errorMessage()).isEqualTo("BACKUP_ENCRYPTION_KEY is not set");
		verify(dumper, never()).dump(any());
	}

	@Test
	void onlyOneBackupRunsAtATime() {
		when(repository.existsByStatus(BackupRun.Status.RUNNING)).thenReturn(true);
		assertThatThrownBy(() -> service(KEY, 30, 12).startNow(1L)).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo("BACKUP_RUNNING"));
		verify(repository, never()).save(any());
	}

	@Test
	void retentionDeletesBackupsBeyondTheDailyAndMonthlyLimits() throws Exception {
		BackupService service = service(KEY, 2, 1);
		String target = "LOCAL " + targetDir.toAbsolutePath().normalize();
		Files.createDirectories(targetDir);
		List<Long> old = new ArrayList<>();
		for (String day : List.of("2026-10-15", "2026-10-14", "2026-09-30")) {
			BackupRun run = new BackupRun(BackupRun.Trigger.SCHEDULED, Instant.parse(day + "T20:30:00Z"), target, null);
			run.setStatus(BackupRun.Status.SUCCESS);
			run.setFileName("team-ops-backup-" + day.replace("-", "") + "T203000Z.tobk");
			Files.writeString(targetDir.resolve(run.getFileName()), "old");
			Files.writeString(targetDir.resolve(run.getFileName() + ".sha256"), "old");
			old.add(repository.save(run).getId());
		}

		Run latest = service.runNow(BackupRun.Trigger.SCHEDULED, null);

		// Kept: today and yesterday (2 daily; October's monthly is today's). Gone: 14 Oct and 30 Sep.
		assertThat(rows.get(old.get(0)).getRemovedAt()).isNull();
		assertThat(rows.get(old.get(1)).getRemovedAt()).isEqualTo(NOW);
		assertThat(rows.get(old.get(2)).getRemovedAt()).isEqualTo(NOW);
		try (Stream<Path> files = Files.list(targetDir)) {
			assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder(latest.fileName(),
					latest.fileName() + ".sha256", "team-ops-backup-20261015T203000Z.tobk",
					"team-ops-backup-20261015T203000Z.tobk.sha256");
		}
		verify(audit).record(eq(AuditAction.BACKUP_REMOVED), isNull(), eq("BACKUP"), isNull(), anyMap(), any());
	}

	private BackupService service(String key, int keepDaily, int keepMonthly) {
		BackupProperties properties = new BackupProperties(true, null, key, null, BackupProperties.TargetType.LOCAL,
				new BackupProperties.Local(targetDir.toString()), null, keepDaily, keepMonthly, 0, workDir.toString());
		@SuppressWarnings("unchecked")
		ObjectProvider<BackupTarget> targets = mock(ObjectProvider.class);
		when(targets.getObject()).thenReturn(new LocalBackupTarget(targetDir));
		return new BackupService(properties, repository, targets, dumper,
				new StorageProperties(temp.resolve("uploads").toString(), 20), audit, JsonMapper.builder().build(),
				new SyncTaskExecutor(), Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata", true);
	}

	private List<Path> workDirFiles() throws IOException {
		if (!Files.exists(workDir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(workDir)) {
			return files.toList();
		}
	}

	private static Map<String, String> unzip(Path file) throws IOException {
		Map<String, String> entries = new java.util.LinkedHashMap<>();
		try (InputStream in = BackupCrypto.decrypt(Files.newInputStream(file), BackupCrypto.key(KEY));
				ZipInputStream zip = new ZipInputStream(in)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		return entries;
	}

	private static String sha256(Path file) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
	}

}
