package com.teamops.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.teamops.backup.entity.BackupRun;
import com.teamops.backup.repository.BackupRunRepository;
import com.teamops.backup.service.BackupCrypto;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.support.IntegrationUsers;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.service.UserService;

/**
 * Phase X3 against MySQL: "Run backup now" dumps the integration test database with the real mysqldump
 * (MYSQLDUMP_PATH), stores an encrypted, checksummed, verified file in a throwaway folder, records it in the history
 * and the audit log; the status page and /actuator/health flag a failed last run; "Export all data" streams every
 * table as CSV with recycle bin rows marked and password hashes redacted. All Super Admin only (403 otherwise).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "app.dev-seed.enabled=false", "app.storage.dir=target/it-uploads",
		"app.scheduling.enabled=false", "app.backup.local.path=target/it-backups",
		"app.backup.work-dir=target/it-backup-work", "app.backup.encryption-key=" + BackupIT.KEY })
class BackupIT {

	static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	private static final Path BACKUPS = Path.of("target/it-backups");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private BackupRunRepository backupRuns;

	@Autowired
	private JdbcTemplate jdbc;

	private IntegrationUsers users;

	private final List<Long> runIds = new ArrayList<>();

	private Long adminId;

	private Long departmentId;

	private final List<Long> taskIds = new ArrayList<>();

	private String adminToken;

	private String employeeToken;

	@BeforeEach
	void setUp() throws Exception {
		users = new IntegrationUsers(userService, userRepository, departmentRepository);
		adminId = users.create("IT", RoleCodes.SUPER_ADMIN);
		adminToken = login(users.email(adminId));
		employeeToken = login(users.email(users.create("IT", RoleCodes.EMPLOYEE)));
	}

	@AfterEach
	void cleanUp() throws IOException {
		runIds.forEach(backupRuns::deleteById);
		for (Long taskId : taskIds) {
			jdbc.update("DELETE FROM recycle_bin_items WHERE entity_type = 'TASK' AND entity_id = ?", taskId);
			jdbc.update("DELETE FROM tasks WHERE id = ?", taskId);
		}
		users.deleteAll();
		if (departmentId != null) {
			departmentRepository.deleteById(departmentId);
		}
		if (Files.isDirectory(BACKUPS)) {
			try (Stream<Path> files = Files.list(BACKUPS)) {
				for (Path file : files.toList()) {
					Files.deleteIfExists(file);
				}
			}
		}
	}

	@Test
	void runBackupNowStoresAVerifiedEncryptedDumpAndTheHealthCheckFlagsFailures() throws Exception {
		for (String path : List.of("/api/admin/backups/status", "/api/admin/backups", "/api/admin/data-export")) {
			as(employeeToken, get(path)).andExpect(status().isForbidden());
		}
		as(employeeToken, post("/api/admin/backups/run")).andExpect(status().isForbidden());

		String started = as(adminToken, post("/api/admin/backups/run")).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("RUNNING"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long runId = ((Number) JsonPath.read(started, "$.id")).longValue();
		runIds.add(runId);
		BackupRun run = awaitFinished(runId);
		assertThat(run.getStatus()).as("backup failed: " + run.getErrorMessage()).isEqualTo(BackupRun.Status.SUCCESS);
		assertThat(run.getRequestedById()).isEqualTo(adminId);
		assertThat(run.getTarget()).isEqualTo("LOCAL " + BACKUPS.toAbsolutePath().normalize());

		// The file and its checksum file are in the target; the checksum matches; the dump is inside.
		Path file = BACKUPS.resolve(run.getFileName());
		assertThat(run.getFileName()).matches("team-ops-backup-\\d{8}T\\d{6}Z\\.tobk");
		assertThat(Files.size(file)).isEqualTo(run.getSizeBytes());
		assertThat(sha256(file)).isEqualTo(run.getChecksum());
		assertThat(Files.readString(BACKUPS.resolve(run.getFileName() + ".sha256"))).startsWith(run.getChecksum());
		String dump = new String(entries(BackupCrypto.decrypt(Files.newInputStream(file), BackupCrypto.key(KEY)))
			.get("database.sql"), StandardCharsets.UTF_8);
		assertThat(dump).contains("CREATE TABLE `backup_runs`", "CREATE TABLE `users`", "INSERT INTO `permissions`");
		// No CREATE DATABASE / USE, so a restore can go into any database name.
		assertThat(dump).doesNotContain("CREATE DATABASE").doesNotContainPattern("(?m)^USE `");

		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'BACKUP_COMPLETED' AND entity_type = 'BACKUP' AND entity_id = ? AND actor_id = ?""",
				Long.class, runId, adminId)).isEqualTo(1);
		as(adminToken, get("/api/admin/backups")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(runId))
			.andExpect(jsonPath("$.content[0].checksum").value(run.getChecksum()));
		as(adminToken, get("/api/admin/backups/status")).andExpect(status().isOk())
			.andExpect(jsonPath("$.healthy").value(true))
			.andExpect(jsonPath("$.lastSuccess.id").value(runId))
			.andExpect(jsonPath("$.running").value(false))
			.andExpect(jsonPath("$.scheduleEnabled").value(false));
		mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));

		// A failed last run: flagged on the status page and as WARNING (still HTTP 200) on the health check.
		BackupRun failed = new BackupRun(BackupRun.Trigger.SCHEDULED, Instant.now(), run.getTarget(), null);
		failed.setStatus(BackupRun.Status.FAILED);
		failed.setFinishedAt(Instant.now());
		failed.setErrorMessage("mysqldump failed (exit 2)");
		runIds.add(backupRuns.save(failed).getId());
		as(adminToken, get("/api/admin/backups/status")).andExpect(jsonPath("$.healthy").value(false))
			.andExpect(jsonPath("$.problems[0].code").value("LAST_FAILED"))
			.andExpect(jsonPath("$.lastSuccess.id").value(runId));
		mvc.perform(get("/actuator/health")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WARNING"));
	}

	@Test
	void exportAllDataHasEveryTableWithRecycleBinRowsMarkedAndSecretsRedacted() throws Exception {
		String code = "EXP_" + Long.toHexString(System.nanoTime()).toUpperCase();
		departmentId = id(as(adminToken, post("/api/departments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Export %s\",\"code\":\"%s\"}".formatted(code, code))).andExpect(status().isCreated()));
		Long assigneeId = users.create(departmentId, RoleCodes.EMPLOYEE);
		Long deletedId = createTask("Exported " + code, assigneeId);
		Long liveId = createTask("Kept " + code, assigneeId);
		as(adminToken, delete("/api/tasks/" + deletedId)).andExpect(status().isNoContent());

		byte[] zip = as(adminToken, get("/api/admin/data-export")).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		Map<String, byte[]> entries = entries(new ByteArrayInputStream(zip));
		assertThat(entries).containsKeys("README.txt", "tables.csv", "tables/users.csv", "tables/tasks.csv",
				"tables/audit_logs.csv", "tables/flyway_schema_history.csv", "tables/backup_runs.csv");
		long tableCount = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
				+ "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'", Long.class);
		assertThat(entries.keySet().stream().filter(n -> n.startsWith("tables/"))).hasSize((int) tableCount);

		// The deleted task is there, marked as in the recycle bin; the live one is marked as not.
		Map<String, CSVRecord> tasks = new LinkedHashMap<>();
		csv(entries.get("tables/tasks.csv")).forEach(r -> tasks.put(r.get("id"), r));
		assertThat(tasks.get(deletedId.toString()).get("_in_recycle_bin")).isEqualTo("yes");
		assertThat(tasks.get(deletedId.toString()).get("title")).isEqualTo("Exported " + code);
		assertThat(tasks.get(liveId.toString()).get("_in_recycle_bin")).isEqualTo("no");
		// Password hashes never leave the app.
		List<CSVRecord> userRows = csv(entries.get("tables/users.csv"));
		assertThat(userRows).isNotEmpty();
		assertThat(userRows.stream().map(r -> r.get("password_hash"))).containsOnly("[redacted]");
		assertThat(csv(entries.get("tables/departments.csv")).get(0).isMapped("_in_recycle_bin")).isFalse();

		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'DATA_EXPORTED' AND actor_id = ? AND JSON_EXTRACT(details, '$.completed') = true""",
				Long.class, adminId)).isEqualTo(1);
	}

	private Long createTask(String title, Long assigneeId) throws Exception {
		Long id = id(as(adminToken, post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
				{"title":"%s","departmentId":%d,"assigneeId":%d,"priority":"MEDIUM"}
				""".formatted(title, departmentId, assigneeId))).andExpect(status().isCreated()));
		taskIds.add(id);
		return id;
	}

	private BackupRun awaitFinished(Long runId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 180_000;
		while (System.currentTimeMillis() < deadline) {
			BackupRun run = backupRuns.findById(runId).orElseThrow();
			if (run.getStatus() != BackupRun.Status.RUNNING) {
				return run;
			}
			Thread.sleep(250);
		}
		throw new AssertionError("The backup did not finish in time");
	}

	private static Map<String, byte[]> entries(InputStream in) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(in)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.put(entry.getName(), zip.readAllBytes());
			}
		}
		return entries;
	}

	private static List<CSVRecord> csv(byte[] content) throws IOException {
		String text = new String(content, StandardCharsets.UTF_8).replaceFirst("^﻿", "");
		return CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader(text))
			.getRecords();
	}

	private static String sha256(Path file) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
	}

	private static Long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private ResultActions as(String token, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private String login(String email) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, IntegrationUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

}
