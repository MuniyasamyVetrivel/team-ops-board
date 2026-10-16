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
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import com.teamops.backup.dto.BackupDtos.CopyStatus;
import com.teamops.backup.dto.BackupDtos.Problem;
import com.teamops.backup.dto.BackupDtos.Run;
import com.teamops.backup.dto.BackupDtos.Status;
import com.teamops.backup.entity.BackupRun;
import com.teamops.backup.entity.BackupRun.Trigger;
import com.teamops.backup.repository.BackupRunRepository;
import com.teamops.backup.service.BackupTargets.Copy;
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
 * the SHA-256 of the encrypted file. The file and a {@code .sha256} sidecar then go to each target
 * ({@link BackupTargets}: the first, and the optional second copy). Each stored copy is read back, its checksum
 * compared and its contents decrypted and opened; the copies succeed or fail on their own, so a broken first target
 * does not stop the second copy. Afterwards the retention policy ({@link BackupRetention}) deletes old backups on each
 * target. One run at a time.
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

	private static final List<BackupRun.Status> FINISHED = List.of(BackupRun.Status.SUCCESS, BackupRun.Status.FAILED);

	private final BackupProperties properties;

	private final BackupRunRepository runs;

	private final BackupTargets targets;

	private final DatabaseDumper dumper;

	private final StorageProperties storage;

	private final AuditService auditService;

	private final ObjectMapper objectMapper;

	private final TaskExecutor executor;

	private final Clock clock;

	private final ZoneId zone;

	private final boolean schedulingEnabled;

	private final AtomicBoolean running = new AtomicBoolean();

	public BackupService(BackupProperties properties, BackupRunRepository runs, BackupTargets targets,
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
		Instant now = clock.instant();
		BackupRun lastSuccess = runs.findFirstByStatusOrderByStartedAtDesc(BackupRun.Status.SUCCESS).orElse(null);
		BackupRun lastFinished = runs.findFirstByStatusInOrderByStartedAtDesc(FINISHED).orElse(null);
		List<CopyStatus> copies = new ArrayList<>();
		copies.add(copyStatus(Copy.PRIMARY, targets.describe(Copy.PRIMARY),
				new BackupHealth.CopyState(lastSuccess == null ? null : finishedAt(lastSuccess),
						lastFinished != null && lastFinished.getStatus() == BackupRun.Status.FAILED,
						lastFinished == null ? null : lastFinished.getErrorMessage()),
				lastFinished == null ? null : lastFinished.getStatus(), now));
		String secondaryTarget = null;
		if (targets.hasSecondary()) {
			secondaryTarget = targets.describe(Copy.SECONDARY);
			BackupRun secondSuccess = runs.findFirstBySecondaryStatusOrderByStartedAtDesc(BackupRun.Status.SUCCESS)
				.orElse(null);
			BackupRun secondFinished = runs.findFirstBySecondaryStatusInOrderByStartedAtDesc(FINISHED).orElse(null);
			copies.add(copyStatus(Copy.SECONDARY, secondaryTarget,
					new BackupHealth.CopyState(secondSuccess == null ? null : finishedAt(secondSuccess),
							secondFinished != null && secondFinished.getSecondaryStatus() == BackupRun.Status.FAILED,
							secondFinished == null ? null : secondFinished.getSecondaryError()),
					secondFinished == null ? null : secondFinished.getSecondaryStatus(), now));
		}
		List<Problem> problems = new ArrayList<>();
		String configurationError = configurationError();
		if (configurationError != null) {
			problems.add(BackupHealth.notConfigured(configurationError));
		}
		copies.forEach(c -> problems.addAll(c.problems()));

		boolean scheduled = properties.enabled() && schedulingEnabled;
		Instant nextRun = null;
		if (scheduled) {
			ZonedDateTime next = CronExpression.parse(properties.cron()).next(now.atZone(zone));
			nextRun = next == null ? null : next.toInstant();
		}
		return new Status(problems.isEmpty(), problems, copies, Run.of(lastSuccess), Run.of(lastFinished),
				running.get() || runs.existsByStatus(BackupRun.Status.RUNNING), scheduled, properties.cron(),
				zone.getId(), nextRun, copies.get(0).target(), secondaryTarget, properties.keepDaily(),
				properties.keepMonthly(), properties.maxAgeHours());
	}

	private CopyStatus copyStatus(Copy copy, String target, BackupHealth.CopyState state, BackupRun.Status lastStatus,
			Instant now) {
		List<Problem> problems = BackupHealth.problems(copy, state, now, properties.maxAgeHours());
		return new CopyStatus(copy.name(), target, problems.isEmpty(), problems, state.lastSuccessAt(),
				lastStatus == null ? null : lastStatus.name(), state.lastFailed() ? state.lastError() : null);
	}

	private static Instant finishedAt(BackupRun run) {
		return run.getFinishedAt() == null ? run.getStartedAt() : run.getFinishedAt();
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

	private BackupRun begin(Trigger trigger, Long actorId) {
		if (!running.compareAndSet(false, true)) {
			throw ApiException.conflict("BACKUP_RUNNING", "A backup is already running");
		}
		try {
			if (runs.existsByStatus(BackupRun.Status.RUNNING)) {
				throw ApiException.conflict("BACKUP_RUNNING", "A backup is already running");
			}
			BackupRun run = new BackupRun(trigger, clock.instant(), targets.describe(Copy.PRIMARY), actorId);
			if (targets.hasSecondary()) {
				run.setSecondaryTarget(targets.describe(Copy.SECONDARY));
				run.setSecondaryStatus(BackupRun.Status.RUNNING);
			}
			return runs.save(run);
		}
		catch (RuntimeException ex) {
			running.set(false);
			throw ex;
		}
	}

	/** The encrypted file waiting in the work folder, ready to be copied to each target. */
	private record Archive(Path file, Path workDir, String name, String checksum, long size, int uploadFiles,
			SecretKey key) {

	}

	private BackupRun execute(Long runId) {
		BackupRun run = runs.findById(runId).orElseThrow();
		Archive archive = null;
		try {
			try {
				archive = build(run);
			}
			catch (Exception | LinkageError ex) {
				log.error("Backup run {} failed", runId, ex);
				return fail(run, rootMessage(ex));
			}
			run.setFileName(archive.name());
			run.setSizeBytes(archive.size());
			run.setChecksum(archive.checksum());
			run.setUploadFiles(archive.uploadFiles());

			String primaryError = storeCopy(Copy.PRIMARY, archive, run::setTarget);
			run.setStatus(primaryError == null ? BackupRun.Status.SUCCESS : BackupRun.Status.FAILED);
			run.setErrorMessage(truncate(primaryError));
			String secondaryError = null;
			boolean secondary = targets.hasSecondary();
			if (secondary) {
				String second = targets.describe(Copy.SECONDARY);
				if (second.equals(targets.describe(Copy.PRIMARY))) {
					run.setSecondaryTarget(second);
					secondaryError = "The second backup target is the same as the first";
				}
				else {
					secondaryError = storeCopy(Copy.SECONDARY, archive, run::setSecondaryTarget);
				}
				run.setSecondaryStatus(secondaryError == null ? BackupRun.Status.SUCCESS : BackupRun.Status.FAILED);
				run.setSecondaryError(truncate(secondaryError));
			}
			run.setFinishedAt(clock.instant());
			run = runs.save(run);

			AuditAction action = primaryError != null ? AuditAction.BACKUP_FAILED
					: secondaryError != null ? AuditAction.BACKUP_COPY_FAILED : AuditAction.BACKUP_COMPLETED;
			Map<String, Object> details = details(run);
			details.put("fileName", archive.name());
			details.put("sizeBytes", archive.size());
			details.put("checksum", archive.checksum());
			details.put("uploadFiles", archive.uploadFiles());
			if (primaryError != null) {
				details.put("error", run.getErrorMessage());
			}
			if (secondaryError != null) {
				details.put("secondaryError", run.getSecondaryError());
			}
			auditService.record(action, run.getRequestedById(), ENTITY_TYPE, run.getId(), details, ClientInfo.unknown());
			log.info("Backup {} ({} bytes): first copy {}, second copy {}", archive.name(), archive.size(),
					run.getStatus(), secondary ? run.getSecondaryStatus() : "not configured");

			if (primaryError == null) {
				applyRetention(Copy.PRIMARY);
			}
			if (secondary && secondaryError == null) {
				applyRetention(Copy.SECONDARY);
			}
			return run;
		}
		finally {
			running.set(false);
			if (archive != null) {
				deleteQuietly(archive.file());
			}
		}
	}

	/** Dumps, zips and encrypts into the work folder. */
	private Archive build(BackupRun run) throws IOException {
		SecretKey key = BackupCrypto.key(properties.encryptionKey());
		Path workDir = BackupProperties.blank(properties.workDir()) ? Path.of(System.getProperty("java.io.tmpdir"))
				: Path.of(properties.workDir());
		Files.createDirectories(workDir);
		Path staging = Files.createTempFile(workDir, "tob-backup-", ".tmp");
		try {
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
			String name = "team-ops-backup-" + FILE_TIME.format(run.getStartedAt()) + EXTENSION;
			return new Archive(staging, workDir, name, HexFormat.of().formatHex(sha.digest()), Files.size(staging),
					uploadFiles, key);
		}
		catch (IOException | RuntimeException ex) {
			deleteQuietly(staging);
			throw ex;
		}
	}

	/**
	 * Stores the file and its checksum file on one target and reads the stored copy back. Returns null on success, or
	 * why the copy failed (the half-written copy is removed).
	 */
	private String storeCopy(Copy copy, Archive archive, Consumer<String> describedAs) {
		BackupTarget target = null;
		try {
			target = targets.get(copy);
			describedAs.accept(target.describe());
			target.store(archive.file(), archive.name());
			Path sidecar = Files.createTempFile(archive.workDir(), "tob-backup-", ".sha256");
			try {
				Files.writeString(sidecar, archive.checksum() + "  " + archive.name() + "\n", StandardCharsets.US_ASCII);
				target.store(sidecar, archive.name() + ".sha256");
			}
			finally {
				Files.deleteIfExists(sidecar);
			}
			verify(target, archive.name(), archive.checksum(), archive.key());
			return null;
		}
		catch (Exception | LinkageError ex) {
			log.error("Storing the {} copy of {} failed", copy, archive.name(), ex);
			if (target != null) {
				removeQuietly(target, archive.name());
			}
			else {
				describedAs.accept(targets.describe(copy));
			}
			return rootMessage(ex);
		}
	}

	/** The whole run failed before any copy could be stored (or the app stopped): every copy is marked failed. */
	private BackupRun fail(BackupRun run, String message) {
		String error = truncate(message == null || message.isBlank() ? "Unknown error" : message);
		run.setStatus(BackupRun.Status.FAILED);
		run.setFinishedAt(clock.instant());
		run.setErrorMessage(error);
		if (run.getSecondaryStatus() != null) {
			run.setSecondaryStatus(BackupRun.Status.FAILED);
			run.setSecondaryError(error);
		}
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
		details.put("status", run.getStatus().name());
		if (run.getSecondaryStatus() != null) {
			details.put("secondaryTarget", run.getSecondaryTarget());
			details.put("secondaryStatus", run.getSecondaryStatus().name());
		}
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

	/** Deletes old backups from one target; each copy has its own history columns. */
	private void applyRetention(Copy copy) {
		try {
			BackupTarget target = targets.get(copy);
			String described = target.describe();
			List<BackupRun> kept = copy == Copy.PRIMARY
					? runs.findByStatusAndTargetAndRemovedAtIsNull(BackupRun.Status.SUCCESS, described)
					: runs.findBySecondaryStatusAndSecondaryTargetAndSecondaryRemovedAtIsNull(BackupRun.Status.SUCCESS,
							described);
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
				if (copy == Copy.PRIMARY) {
					old.setRemovedAt(clock.instant());
				}
				else {
					old.setSecondaryRemovedAt(clock.instant());
				}
				runs.save(old);
				removed.add(old.getFileName());
			}
			if (!removed.isEmpty()) {
				Map<String, Object> details = new LinkedHashMap<>();
				details.put("copy", copy.name());
				details.put("target", described);
				details.put("files", removed);
				details.put("keepDaily", properties.keepDaily());
				details.put("keepMonthly", properties.keepMonthly());
				auditService.record(AuditAction.BACKUP_REMOVED, null, ENTITY_TYPE, null, details, ClientInfo.unknown());
				log.info("Retention removed {} old backups from the {} target", removed.size(), copy);
			}
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Backup retention on the {} target could not finish: {}", copy, ex.getMessage());
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

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			log.warn("Could not delete the backup staging file {}", file);
		}
	}

	private static String truncate(String message) {
		return message == null || message.length() <= MAX_ERROR ? message : message.substring(0, MAX_ERROR);
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
