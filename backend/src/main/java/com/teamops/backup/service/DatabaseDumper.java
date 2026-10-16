package com.teamops.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dumps the application database with mysqldump (MYSQLDUMP_PATH): one consistent InnoDB snapshot, triggers included,
 * without CREATE DATABASE or USE so a restore can go into any database name. The password is handed over in a
 * temporary option file, never on the command line where other users could see it.
 */
@Component
public class DatabaseDumper {

	private static final Pattern JDBC_URL = Pattern
		.compile("^jdbc:mysql://([^:/?,]+)(?::(\\d+))?/([A-Za-z0-9_$-]+)(?:[?].*)?$");

	private static final int MAX_STDERR = 4000;

	private final String mysqldump;

	private final Connection connection;

	public DatabaseDumper(BackupProperties properties, @Value("${spring.datasource.url}") String url,
			@Value("${spring.datasource.username}") String username,
			@Value("${spring.datasource.password:}") String password) {
		this.mysqldump = properties.mysqldumpPath();
		this.connection = Connection.parse(url, username, password);
	}

	/** Where the dump comes from, parsed from DB_URL. */
	public record Connection(String host, int port, String database, String username, String password) {

		public static Connection parse(String jdbcUrl, String username, String password) {
			Matcher m = JDBC_URL.matcher(jdbcUrl == null ? "" : jdbcUrl.trim());
			if (!m.matches()) {
				throw new IllegalArgumentException("Backups need a jdbc:mysql://host:port/database URL");
			}
			int port = m.group(2) == null ? 3306 : Integer.parseInt(m.group(2));
			return new Connection(m.group(1), port, m.group(3), username, password);
		}

		@Override
		public String toString() {
			return "Connection[" + host + ":" + port + "/" + database + "]";
		}

	}

	public String database() {
		return connection.database();
	}

	/** Writes the SQL dump to {@code out} (left open). */
	public void dump(OutputStream out) throws IOException {
		Path options = optionFile();
		Process process = null;
		try {
			List<String> command = List.of(mysqldump, "--defaults-extra-file=" + options, "--single-transaction",
					"--quick", "--triggers", "--hex-blob", "--no-tablespaces", "--set-gtid-purged=OFF",
					"--default-character-set=utf8mb4", connection.database());
			try {
				process = new ProcessBuilder(command).start();
			}
			catch (IOException ex) {
				throw new IOException("mysqldump could not be started (check MYSQLDUMP_PATH): " + ex.getMessage(), ex);
			}
			process.getOutputStream().close();
			ByteArrayOutputStream stderr = new ByteArrayOutputStream();
			Thread errors = drain(process.getErrorStream(), stderr);
			try (InputStream dump = process.getInputStream()) {
				dump.transferTo(out);
			}
			int exit = process.waitFor();
			errors.join();
			if (exit != 0) {
				throw new IOException("mysqldump failed (exit " + exit + "): " + errorText(stderr));
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while dumping the database", ex);
		}
		finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
			Files.deleteIfExists(options);
		}
	}

	private Path optionFile() throws IOException {
		Path file;
		try {
			file = Files.createTempFile("tob-mysqldump-", ".cnf",
					PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		}
		catch (UnsupportedOperationException ex) {
			// Windows: the file lands in the user's own temp folder.
			file = Files.createTempFile("tob-mysqldump-", ".cnf");
		}
		String content = "[client]\nhost=" + quote(connection.host()) + "\nport=" + connection.port() + "\nuser="
				+ quote(connection.username()) + "\npassword=" + quote(connection.password()) + "\n";
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	/** Option file values in double quotes, with backslashes and quotes escaped. */
	static String quote(String value) {
		String v = value == null ? "" : value;
		return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static Thread drain(InputStream in, ByteArrayOutputStream sink) {
		Thread thread = new Thread(() -> {
			byte[] buffer = new byte[4096];
			try (in) {
				int n;
				while ((n = in.read(buffer)) >= 0) {
					synchronized (sink) {
						if (sink.size() < MAX_STDERR) {
							sink.write(buffer, 0, Math.min(n, MAX_STDERR - sink.size()));
						}
					}
				}
			}
			catch (IOException ignored) {
				// the process ended
			}
		}, "mysqldump-stderr");
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	private static String errorText(ByteArrayOutputStream stderr) {
		String text;
		synchronized (stderr) {
			text = stderr.toString(StandardCharsets.UTF_8);
		}
		String cleaned = text.lines()
			.filter(line -> !line.contains("[Warning]"))
			.reduce((a, b) -> a + " " + b)
			.orElse(text)
			.trim();
		return cleaned.isEmpty() ? "no error output" : cleaned;
	}

}
