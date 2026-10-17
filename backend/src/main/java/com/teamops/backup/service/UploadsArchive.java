package com.teamops.backup.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Adds every uploaded file (FILE_STORAGE_DIR) to a ZIP as {@code uploads/<relative path>}, for backups and exports. */
final class UploadsArchive {

	static final String PREFIX = "uploads/";

	private UploadsArchive() {
	}

	/**
	 * Returns the number of files added. Symbolic links are skipped; a file deleted while the ZIP is written (someone
	 * removed an attachment) is skipped instead of failing the whole backup.
	 */
	static int addTo(ZipOutputStream zip, String storageDir) throws IOException {
		return addTo(zip, storageDir, Set.of());
	}

	/** As {@link #addTo(ZipOutputStream, String)}, leaving out the files with these storage keys (relative paths). */
	static int addTo(ZipOutputStream zip, String storageDir, Set<String> excludedKeys) throws IOException {
		Path root = Path.of(storageDir).toAbsolutePath().normalize();
		if (!Files.isDirectory(root)) {
			return 0;
		}
		List<Path> files;
		try (Stream<Path> walk = Files.walk(root)) {
			files = walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
		}
		int added = 0;
		for (Path file : files) {
			String key = root.relativize(file).toString().replace('\\', '/');
			if (excludedKeys.contains(key)) {
				continue;
			}
			InputStream in;
			try {
				in = Files.newInputStream(file);
			}
			catch (NoSuchFileException ex) {
				continue;
			}
			try (in) {
				zip.putNextEntry(new ZipEntry(PREFIX + key));
				in.transferTo(zip);
				zip.closeEntry();
			}
			added++;
		}
		return added;
	}

}
