package com.teamops.backup.service;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Backup encryption: AES-256-GCM in 1 MiB chunks, so files of any size are encrypted and decrypted as streams while
 * every chunk is still authenticated. {@code scripts/BackupTool.java} reads the same format for a restore; keep the two
 * in step ({@code BackupCryptoTest} decrypts with the script).
 *
 * <pre>
 * header  "TOBKUP1\n" (8 bytes) | chunk size (int) | nonce prefix (8 random bytes)
 * chunk   last flag (1 byte: 0 more, 1 last) | ciphertext length (int) | ciphertext with 16-byte tag
 * nonce   nonce prefix | chunk number (int)        AAD   header | last flag
 * </pre>
 *
 * The last flag is authenticated, so a cut-off file is detected: it must end with exactly one last chunk.
 */
public final class BackupCrypto {

	static final byte[] MAGIC = "TOBKUP1\n".getBytes(StandardCharsets.US_ASCII);

	static final int CHUNK_SIZE = 1024 * 1024;

	private static final int MAX_CHUNK_SIZE = 16 * 1024 * 1024;

	private static final int HEADER_LENGTH = MAGIC.length + 4 + 8;

	private static final int TAG_BITS = 128;

	private static final int TAG_BYTES = TAG_BITS / 8;

	private static final SecureRandom RANDOM = new SecureRandom();

	private BackupCrypto() {
	}

	/** Parses BACKUP_ENCRYPTION_KEY: base64 of exactly 32 bytes. */
	public static SecretKey key(String base64) {
		if (base64 == null || base64.isBlank()) {
			throw new IllegalStateException("BACKUP_ENCRYPTION_KEY is not set");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("BACKUP_ENCRYPTION_KEY is not valid base64");
		}
		if (bytes.length != 32) {
			throw new IllegalStateException("BACKUP_ENCRYPTION_KEY must be 32 bytes (base64 of 32 random bytes)");
		}
		return new SecretKeySpec(bytes, "AES");
	}

	/** A new random key, base64 encoded, for BACKUP_ENCRYPTION_KEY. */
	public static String newKey() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

	public static OutputStream encrypt(OutputStream out, SecretKey key) throws IOException {
		return new EncryptingStream(out, key, CHUNK_SIZE);
	}

	static OutputStream encrypt(OutputStream out, SecretKey key, int chunkSize) throws IOException {
		return new EncryptingStream(out, key, chunkSize);
	}

	public static InputStream decrypt(InputStream in, SecretKey key) throws IOException {
		return new DecryptingStream(in, key);
	}

	private static byte[] nonce(byte[] prefix, int counter) {
		return ByteBuffer.allocate(12).put(prefix).putInt(counter).array();
	}

	private static final class EncryptingStream extends FilterOutputStream {

		private final SecretKey key;

		private final byte[] header;

		private final byte[] prefix = new byte[8];

		private final byte[] buffer;

		private int buffered;

		private int counter;

		private boolean closed;

		EncryptingStream(OutputStream out, SecretKey key, int chunkSize) throws IOException {
			super(out);
			this.key = key;
			this.buffer = new byte[chunkSize];
			RANDOM.nextBytes(prefix);
			header = ByteBuffer.allocate(HEADER_LENGTH).put(MAGIC).putInt(chunkSize).put(prefix).array();
			out.write(header);
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[] { (byte) b }, 0, 1);
		}

		@Override
		public void write(byte[] data, int offset, int length) throws IOException {
			while (length > 0) {
				if (buffered == buffer.length) {
					// Only now is it known that more data follows, so this chunk is not the last one.
					writeChunk(false);
				}
				int n = Math.min(length, buffer.length - buffered);
				System.arraycopy(data, offset, buffer, buffered, n);
				buffered += n;
				offset += n;
				length -= n;
			}
		}

		@Override
		public void flush() throws IOException {
			out.flush();
		}

		@Override
		public void close() throws IOException {
			if (closed) {
				return;
			}
			closed = true;
			try {
				writeChunk(true);
				out.flush();
			}
			finally {
				out.close();
			}
		}

		private void writeChunk(boolean last) throws IOException {
			byte flag = (byte) (last ? 1 : 0);
			try {
				Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
				cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce(prefix, counter++)));
				cipher.updateAAD(header);
				cipher.updateAAD(new byte[] { flag });
				byte[] sealed = cipher.doFinal(buffer, 0, buffered);
				out.write(flag);
				out.write(ByteBuffer.allocate(4).putInt(sealed.length).array());
				out.write(sealed);
			}
			catch (GeneralSecurityException ex) {
				throw new IOException("Could not encrypt the backup", ex);
			}
			buffered = 0;
		}

	}

	private static final class DecryptingStream extends InputStream {

		private final DataInputStream in;

		private final SecretKey key;

		private final byte[] header;

		private final byte[] prefix;

		private final int chunkSize;

		private byte[] chunk = new byte[0];

		private int position;

		private int counter;

		private boolean done;

		DecryptingStream(InputStream source, SecretKey key) throws IOException {
			this.in = new DataInputStream(source);
			this.key = key;
			header = new byte[HEADER_LENGTH];
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
			if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) {
				throw new IOException("Not a Team Ops backup (bad chunk size)");
			}
			prefix = new byte[8];
			view.get(prefix);
		}

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			int n = read(one, 0, 1);
			return n < 0 ? -1 : one[0] & 0xFF;
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
				nextChunk();
			}
			int n = Math.min(length, chunk.length - position);
			System.arraycopy(chunk, position, target, offset, n);
			position += n;
			return n;
		}

		private void nextChunk() throws IOException {
			int flag;
			int length;
			try {
				flag = in.readUnsignedByte();
				length = in.readInt();
			}
			catch (EOFException ex) {
				throw new IOException("The backup file is truncated");
			}
			if ((flag != 0 && flag != 1) || length < TAG_BYTES || length > chunkSize + TAG_BYTES) {
				throw new IOException("The backup file is damaged");
			}
			byte[] sealed = new byte[length];
			try {
				in.readFully(sealed);
			}
			catch (EOFException ex) {
				throw new IOException("The backup file is truncated");
			}
			try {
				Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
				cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce(prefix, counter++)));
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

		@Override
		public void close() throws IOException {
			in.close();
		}

	}

}
