package com.teamops.backup.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import com.teamops.backup.dto.BackupDtos.Problem;
import com.teamops.backup.dto.BackupDtos.Run;
import com.teamops.backup.dto.BackupDtos.Status;
import com.teamops.backup.entity.BackupRun;
import com.teamops.backup.entity.BackupRun.Trigger;
import com.teamops.backup.repository.BackupRunRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.storage.StorageProperties;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Backups of the database and the uploads folder. A run dumps the database (mysqldump), zips the dump with every
 * uploaded file and a manifest, encrypts the ZIP ({@link BackupCrypto}, key from BACKUP_ENCRYPTION_KEY) and records
 * the SHA-256 of the encrypted file. The file and a {@code .sha256} sidecar go to the {@link BackupTarget}; the stored
 * copy is then read back, its checksum compared and its contents decrypted and opened, before the run counts as a
 * success. Afterwards the retention policy ({@link BackupRetention}) deletes old backups. One run at a time.
 */
@Slf4j
@Service
public class BackupService {

	public static final String ENTITY_TYPE = "BACKUP";

	static final String DUMP_ENTRY = "database.sql";

	static final String EXTENSION = ".tobk";

	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
		.withZone(ZoneOffset.UTC);

	private static final int MAX_ERROR = 2000;

	private final BackupProperties properties;

	private final BackupRunRepository runs;

	private final ObjectProvider<BackupTarget> targets;

	private final DatabaseDumper dumper;

	private final StorageProperties storage;

	private final AuditService auditService;

	private final ObjectMapper objectMapper;

	private final TaskExecutor executor;

	private final Clock clock;

	private final ZoneId zone;

	private final boolean schedulingEnabled;

	private final AtomicBoolean running = new AtomicBoolean();

	public BackupService(BackupProperties properties, BackupRunRepository runs, ObjectProvider<BackupTarget> targets,
			DatabaseDumper dumper, StorageProperties storage, AuditService auditService, ObjectMapper objectMapper,
			@Qualifier("applicationTaskExecutor") TaskExecutor executor, Clock clock,
			@Value("${app.time-zone:Asia/Kolkata}") String zone,
			@Value("${app.scheduling.enabled:true}") boolean schedulingEnabled) {
		this.properties = properties;
		this.runs = runs;
		this.targets = targets;
		this.dumper = dumper;
		this.storage = storage;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
		this.executor = executor;
		this.clock = clock;
		this.zone = ZoneId.of(zone);
		this.schedulingEnabled = schedulingEnabled;
	}

	/** A run left RUNNING by a stopped app never finishes: mark it failed so the status is truthful. */
	@EventListener(ApplicationReadyEvent.class)
	public void failInterruptedRuns() {
		for (BackupRun run : runs.findByStatus(BackupRun.Status.RUNNING)) {
			fail(run, "Interrupted: the application stopped during the backup");
		}
	}

	/** "Run backup now": starts a run in the background and returns it (RUNNING). 409 while one is running. */
	public Run startNow(Long actorId) {
		BackupRun run = begin(Trigger.MANUAL, actorId);
		try {
			executor.execute(() -> execute(run.getId()));
		}
		catch (RuntimeException ex) {
			running.set(false);
			fail(run, "Could not start the backup: " + ex.getMessage());
			throw ex;
		}
		return Run.of(run);
	}

	/** The nightly run, in the scheduler's thread. Skipped when a run is already going. */
	public void runScheduled() {
		BackupRun run;
		try {
			run = begin(Trigger.SCHEDULED, null);
		}
		catch (ApiException ex) {
			log.info("Nightly backup skipped: a backup is already running");
			return;
		}
		execute(run.getId());
	}

	/** Runs a backup to completion in the calling thread (used by the schedule and tests). */
	public Run runNow(Trigger trigger, Long actorId) {
		BackupRun run = begin(trigger, actorId);
		return Run.of(execute(run.getId()));
	}

	public PageResponse<Run> history(Pageable pageable) {
		return PageResponse.of(runs.findAllBy(pageable).map(Run::of));
	}

	public Status status() {
		BackupRun lastSuccess = runs.findFirstByStatusOrderByStartedAtDesc(BackupRun.Status.SUCCESS).orElse(null);
		BackupRun lastFinished = runs
			.findFirstByStatusInOrderByStartedAtDesc(List.of(BackupRun.Status.SUCCESS, BackupRun.Status.FAILED))
			.orElse(null);
		List<Problem> problems = BackupHealth.problems(lastSuccess, lastFinished, configurationError(),
				clock.instant(), properties.maxAgeHours());
		boolean scheduled = properties.enabled() && schedulingEnabled;
		Instant nextRun = null;
		if (scheduled) {
			ZonedDateTime next = CronExpression.parse(properties.cron()).next(ZonedDateTime.now(clock).withZoneSameInstant(zone));
			nextRun = next == null ? null : next.toInstant();
		}
		return new Status(problems.isEmpty(), problems, Run.of(lastSuccess), Run.of(lastFinished),
				running.get() || runs.existsByStatus(BackupRun.Status.RUNNING), scheduled, properties.cron(),
				zone.getId(), nextRun, describeTarget(), properties.keepDaily(), properties.keepMonthly(),
				properties.maxAgeHours());
	}

	/** Why a backup cannot run at all, or null. */
	String configurationError() {
		try {
			BackupCrypto.key(properties.encryptionKey());
			return null;
		}
		catch (IllegalStateException ex) {
			return ex.getMessage();
		}
	}

	private String describeTarget() {
		try {
			return targets.getObject().describe();
		}
		catch (RuntimeException ex) {
			return properties.target().name() + " (not available: " + rootMessage(ex) + ")";
		}
	}

	private BackupRun begin(Trigger trigger, Long actorId) {
		if (!running.compareAndSet(false, true)) {
			throw ApiException.conflict("BACKUP_RUNNING", "A backup is already running");
		}
		try {
			if (runs.existsByStatus(BackupRun.Status.RUNNING)) {
				throw ApiException.conflict("BACKUP_RUNNING", "A backup is already running");
			}
			return runs.save(new BackupRun(trigger, clock.instant(), describeTarget(), actorId));
		}
		catch (RuntimeException ex) {
			running.set(false);
			throw ex;
		}
	}

	private BackupRun execute(Long runId) {
		BackupRun run = runs.findById(runId).orElseThrow();
		Path staging = null;
		BackupTarget target = null;
		String name = "team-ops-backup-" + FILE_TIME.format(run.getStartedAt()) + EXTENSION;
		boolean stored = false;
		try {
			SecretKey key = BackupCrypto.key(properties.encryptionKey());
			target = targets.getObject();
			run.setTarget(target.describe());
			Path workDir = BackupProperties.blank(properties.workDir()) ? Path.of(System.getProperty("java.io.tmpdir"))
					: Path.of(properties.workDir());
			Files.createDirectories(workDir);
			staging = Files.createTempFile(workDir, "tob-backup-", ".tmp");

			MessageDigest sha = sha256();
			int uploadFiles;
			try (ZipOutputStream zip = new ZipOutputStream(BackupCrypto
				.encrypt(new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(staging)), sha), key))) {
				zip.putNextEntry(new ZipEntry(DUMP_ENTRY));
				dumper.dump(new NonClosingOutputStream(zip));
				zip.closeEntry();
				uploadFiles = UploadsArchive.addTo(zip, storage.dir());
				zip.putNextEntry(new ZipEntry("manifest.json"));
				zip.write(manifest(run, uploadFiles));
				zip.closeEntry();
			}
			String checksum = HexFormat.of().formatHex(sha.digest());
			long size = Files.size(staging);

			stored = true;
			target.store(staging, name);
			Path sidecar = Files.createTempFile(workDir, "tob-backup-", ".sha256");
			try {
				Files.writeString(sidecar, checksum + "  " + name + "\n", StandardCharsets.US_ASCII);
				target.store(sidecar, name + ".sha256");
			}
			finally {
				Files.deleteIfExists(sidecar);
			}
			verify(target, name, checksum, key);

			run.setStatus(BackupRun.Status.SUCCESS);
			run.setFinishedAt(clock.instant());
			run.setFileName(name);
			run.setSizeBytes(size);
			run.setChecksum(checksum);
			run.setUploadFiles(uploadFiles);
			run = runs.save(run);
			Map<String, Object> details = details(run);
			details.put("fileName", name);
			details.put("sizeBytes", size);
			details.put("checksum", checksum);
			details.put("uploadFiles", uploadFiles);
			auditService.record(AuditAction.BACKUP_COMPLETED, run.getRequestedById(), ENTITY_TYPE, run.getId(), details,
					ClientInfo.unknown());
			log.info("Backup {} stored on {} ({} bytes)", name, run.getTarget(), size);
			applyRetention(target);
			return run;
		}
		catch (Exception | LinkageError ex) {
			log.error("Backup run {} failed", runId, ex);
			if (stored && target != null) {
				removeQuietly(target, name);
			}
			return fail(run, rootMessage(ex));
		}
		finally {
			running.set(false);
			if (staging != null) {
				try {
					Files.deleteIfExists(staging);
				}
				catch (IOException ex) {
					log.warn("Could not delete the backup staging file {}", staging);
				}
			}
		}
	}

	private BackupRun fail(BackupRun run, String message) {
		String error = message == null || message.isBlank() ? "Unknown error" : message;
		run.setStatus(BackupRun.Status.FAILED);
		run.setFinishedAt(clock.instant());
		run.setErrorMessage(error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error);
		BackupRun saved = runs.save(run);
		Map<String, Object> details = details(saved);
		details.put("error", saved.getErrorMessage());
		auditService.record(AuditAction.BACKUP_FAILED, saved.getRequestedById(), ENTITY_TYPE, saved.getId(), details,
				ClientInfo.unknown());
		return saved;
	}

	private Map<String, Object> details(BackupRun run) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("trigger", run.getTrigger().name());
		details.put("target", run.getTarget());
		return details;
	}

	private byte[] manifest(BackupRun run, int uploadFiles) {
		Map<String, Object> manifest = new LinkedHashMap<>();
		manifest.put("format", "team-ops-backup");
		manifest.put("formatVersion", 1);
		manifest.put("createdAt", run.getStartedAt().toString());
		manifest.put("database", dumper.database());
		manifest.put("databaseDump", DUMP_ENTRY);
		manifest.put("uploadFiles", uploadFiles);
		return objectMapper.writeValueAsBytes(manifest);
	}

	/**
	 * Reads the stored copy back: the checksum must match, it must decrypt (every chunk authenticated) and the ZIP
	 * must hold a non-empty database dump.
	 */
	private void verify(BackupTarget target, String name, String expected, SecretKey key) throws IOException {
		MessageDigest sha = sha256();
		boolean dumpFound = false;
		try (DigestInputStream digest = new DigestInputStream(target.open(name), sha)) {
			InputStream plain = BackupCrypto.decrypt(new BufferedInputStream(digest), key);
			ZipInputStream zip = new ZipInputStream(plain);
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				long read = zip.transferTo(OutputStream.nullOutputStream());
				if (DUMP_ENTRY.equals(entry.getName()) && read > 0) {
					dumpFound = true;
				}
			}
			// Read to the end: the last chunk is authenticated and the checksum covers the whole file.
			plain.transferTo(OutputStream.nullOutputStream());
		}
		String actual = HexFormat.of().formatHex(sha.digest());
		if (!actual.equals(expected)) {
			throw new IOException("Checksum mismatch after writing: expected " + expected + ", stored copy " + actual);
		}
		if (!dumpFound) {
			throw new IOException("The stored backup has no database dump");
		}
	}

	private void applyRetention(BackupTarget target) {
		try {
			List<BackupRun> kept = runs.findByStatusAndTargetAndRemovedAtIsNull(BackupRun.Status.SUCCESS,
					target.describe());
			Set<Long> remove = BackupRetention.toDelete(
					kept.stream().map(r -> new BackupRetention.Candidate(r.getId(), r.getStartedAt())).toList(),
					properties.keepDaily(), properties.keepMonthly(), zone);
			List<String> removed = new ArrayList<>();
			for (BackupRun old : kept) {
				if (!remove.contains(old.getId())) {
					continue;
				}
				target.delete(old.getFileName());
				target.delete(old.getFileName() + ".sha256");
				old.setRemovedAt(clock.instant());
				runs.save(old);
				removed.add(old.getFileName());
			}
			if (!removed.isEmpty()) {
				Map<String, Object> details = new LinkedHashMap<>();
				details.put("target", target.describe());
				details.put("files", removed);
				details.put("keepDaily", properties.keepDaily());
				details.put("keepMonthly", properties.keepMonthly());
				auditService.record(AuditAction.BACKUP_REMOVED, null, ENTITY_TYPE, null, details, ClientInfo.unknown());
				log.info("Retention removed {} old backups", removed.size());
			}
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Backup retention could not finish: {}", ex.getMessage());
		}
	}

	private static void removeQuietly(BackupTarget target, String name) {
		try {
			target.delete(name);
			target.delete(name + ".sha256");
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Could not remove the failed backup {}: {}", name, ex.getMessage());
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Our own I/O errors already say what went wrong; for anything else (a bean that failed) the root cause does. */
	static String rootMessage(Throwable ex) {
		if (ex instanceof IOException && ex.getMessage() != null) {
			return ex.getMessage();
		}
		Throwable cause = ex;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
	}

}
