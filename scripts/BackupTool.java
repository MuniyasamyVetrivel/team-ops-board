import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Team Ops Board backup tool, used by restore-backup.ps1 and restore-backup.sh. Needs a JDK 21 (runs as a single
 * source file: {@code java BackupTool.java ...}), no other dependencies.
 *
 * <pre>
 * java BackupTool.java verify  &lt;backup.tobk&gt; [checksum]
 * java BackupTool.java extract &lt;backup.tobk&gt; &lt;empty output folder&gt; [checksum]
 * </pre>
 *
 * The key comes from the BACKUP_ENCRYPTION_KEY environment variable (never the command line). The checksum is the
 * SHA-256 hex from the backup history; without it, the {@code .sha256} file next to the backup is used. Both commands
 * check the checksum first, then decrypt every chunk (each is authenticated); extract writes database.sql,
 * manifest.json and uploads/ into the output folder.
 *
 * The format matches com.teamops.backup.service.BackupCrypto: header "TOBKUP1\n" | chunk size | 8-byte nonce prefix,
 * then chunks of last flag | length | AES-256-GCM ciphertext (nonce = prefix | chunk number, AAD = header | flag).
 */
public class BackupTool {

	private static final byte[] MAGIC = "TOBKUP1\n".getBytes(StandardCharsets.US_ASCII);

	private static final int HEADER_LENGTH = MAGIC.length + 4 + 8;

	public static void main(String[] args) {
		try {
			run(args);
		}
		catch (IllegalArgumentException ex) {
			System.err.println("ERROR: " + ex.getMessage());
			System.err.println("Usage: java BackupTool.java verify <backup.tobk> [checksum]");
			System.err.println("       java BackupTool.java extract <backup.tobk> <empty output folder> [checksum]");
			System.exit(2);
		}
		catch (Exception ex) {
			System.err.println("ERROR: " + ex.getMessage());
			System.exit(1);
		}
	}

	static void run(String[] args) throws Exception {
		if (args.length < 2) {
			throw new IllegalArgumentException("missing arguments");
		}
		String command = args[0];
		Path backup = Path.of(args[1]);
		if (!Files.isRegularFile(backup)) {
			throw new IllegalArgumentException("backup file not found: " + backup);
		}
		Path output = null;
		String checksum;
		if ("verify".equals(command)) {
			checksum = args.length > 2 ? args[2] : null;
		}
		else if ("extract".equals(command)) {
			if (args.length < 3) {
				throw new IllegalArgumentException("missing output folder");
			}
			output = Path.of(args[2]).toAbsolutePath().normalize();
			checksum = args.length > 3 ? args[3] : null;
			if (Files.exists(output)) {
				try (var entries = Files.list(output)) {
					if (entries.findAny().isPresent()) {
						throw new IllegalArgumentException("output folder is not empty: " + output);
					}
				}
			}
			Files.createDirectories(output);
		}
		else {
			throw new IllegalArgumentException("unknown command " + command);
		}
		SecretKey key = key(System.getenv("BACKUP_ENCRYPTION_KEY"));
		String expected = expectedChecksum(backup, checksum);

		String actual = sha256(backup);
		if (!actual.equalsIgnoreCase(expected)) {
			throw new IOException("Checksum mismatch: expected " + expected + ", file has " + actual);
		}
		System.out.println("Checksum OK: " + actual);

		long files = 0;
		boolean dump = false;
		DecryptingStream plain = new DecryptingStream(new BufferedInputStream(Files.newInputStream(backup)), key);
		try (ZipInputStream zip = new ZipInputStream(plain)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				long size;
				if (output != null) {
					Path target = output.resolve(entry.getName()).normalize();
					if (!target.startsWith(output)) {
						throw new IOException("Unsafe path in backup: " + entry.getName());
					}
					Files.createDirectories(target.getParent());
					try (OutputStream out = Files.newOutputStream(target)) {
						size = zip.transferTo(out);
					}
				}
				else {
					size = zip.transferTo(OutputStream.nullOutputStream());
				}
				if ("database.sql".equals(entry.getName())) {
					dump = size > 0;
				}
				if (entry.getName().startsWith("uploads/")) {
					files++;
				}
			}
			// Read to the end so the last chunk is authenticated too.
			plain.transferTo(OutputStream.nullOutputStream());
		}
		if (!dump) {
			throw new IOException("The backup has no database dump");
		}
		System.out.println("Decrypted OK: database.sql and " + files + " uploaded files"
				+ (output == null ? "" : " written to " + output));
	}

	static SecretKey key(String base64) {
		if (base64 == null || base64.isBlank()) {
			throw new IllegalArgumentException("set the BACKUP_ENCRYPTION_KEY environment variable");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("BACKUP_ENCRYPTION_KEY is not valid base64");
		}
		if (bytes.length != 32) {
			throw new IllegalArgumentException("BACKUP_ENCRYPTION_KEY must be base64 of 32 bytes");
		}
		return new SecretKeySpec(bytes, "AES");
	}

	static String expectedChecksum(Path backup, String given) throws IOException {
		if (given != null && !given.isBlank()) {
			return given.trim();
		}
		Path sidecar = backup.resolveSibling(backup.getFileName() + ".sha256");
		if (!Files.isRegularFile(sidecar)) {
			throw new IllegalArgumentException(
					"no checksum given and " + sidecar.getFileName() + " not found next to the backup");
		}
		String line = Files.readString(sidecar, StandardCharsets.US_ASCII).trim();
		String hex = line.split("\\s+")[0];
		if (!hex.matches("[0-9a-fA-F]{64}")) {
			throw new IOException("Unreadable checksum file " + sidecar);
		}
		return hex;
	}

	static String sha256(Path file) throws IOException, GeneralSecurityException {
		MessageDigest sha = MessageDigest.getInstance("SHA-256");
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[1 << 16];
			int n;
			while ((n = in.read(buffer)) > 0) {
				sha.update(buffer, 0, n);
			}
		}
		return HexFormat.of().formatHex(sha.digest());
	}

	static final class DecryptingStream extends InputStream {

		private final DataInputStream in;

		private final SecretKey key;

		private final byte[] header = new byte[HEADER_LENGTH];

		private final byte[] prefix = new byte[8];

		private final int chunkSize;

		private byte[] chunk = new byte[0];

		private int position;

		private int counter;

		private boolean done;

		DecryptingStream(InputStream source, SecretKey key) throws IOException {
			this.in = new DataInputStream(source);
			this.key = key;
			try {
				in.readFully(header);
			}
			catch (EOFException ex) {
				throw new IOException("Not a Team Ops backup (file too short)");
			}
			if (!Arrays.equals(Arrays.copyOf(header, MAGIC.length), MAGIC)) {
				throw new IOException("Not a Team Ops backup (unknown header)");
			}
			ByteBuffer view = ByteBuffer.wrap(header, MAGIC.length, 12);
			chunkSize = view.getInt();
			if (chunkSize <= 0 || chunkSize > 16 * 1024 * 1024) {
				throw new IOException("Not a Team Ops backup (bad chunk size)");
			}
			view.get(prefix);
		}

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
		}

		@Override
		public int read(byte[] target, int offset, int length) throws IOException {
			if (length == 0) {
				return 0;
			}
			while (position == chunk.length) {
				if (done) {
					return -1;
				}
				next();
			}
			int n = Math.min(length, chunk.length - position);
			System.arraycopy(chunk, position, target, offset, n);
			position += n;
			return n;
		}

		private void next() throws IOException {
			int flag;
			byte[] sealed;
			try {
				flag = in.readUnsignedByte();
				int length = in.readInt();
				if ((flag != 0 && flag != 1) || length < 16 || length > chunkSize + 16) {
					throw new IOException("The backup file is damaged");
				}
				sealed = new byte[length];
				in.readFully(sealed);
			}
			catch (EOFException ex) {
				throw new IOException("The backup file is truncated");
			}
			try {
				Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
				byte[] nonce = ByteBuffer.allocate(12).put(prefix).putInt(counter++).array();
				cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
				cipher.updateAAD(header);
				cipher.updateAAD(new byte[] { (byte) flag });
				chunk = cipher.doFinal(sealed);
			}
			catch (GeneralSecurityException ex) {
				throw new IOException("The backup cannot be decrypted: wrong key or damaged file");
			}
			position = 0;
			if (flag == 1) {
				done = true;
				if (in.read() != -1) {
					throw new IOException("The backup file has unexpected data after its end");
				}
			}
		}

	}

}
