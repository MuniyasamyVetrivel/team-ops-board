package com.teamops.common.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.exception.ApiException;
import com.teamops.common.settings.AppSettingsService;

import lombok.RequiredArgsConstructor;

/**
 * Validates uploads, stores the bytes and records metadata. Bytes are removed again if the surrounding transaction
 * rolls back, and deleted files are removed from storage only after the delete commits.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class FileService {

	private final FileStorageService storage;

	private final StoredFileRepository repository;

	private final StorageProperties properties;

	private final AppSettingsService settings;

	public StoredFile store(MultipartFile upload, Long uploadedBy) {
		UploadPolicy.Checked checked = UploadPolicy.check(upload.getOriginalFilename(), upload.getSize(),
				settings.uploadMaxBytes(properties.maxFileSizeMb()));
		try (InputStream in = upload.getInputStream()) {
			return save(in, checked, upload.getSize(), uploadedBy);
		}
		catch (IOException ex) {
			throw uploadFailed();
		}
	}

	/**
	 * Stores content the server produced itself (for example a resized profile photo), with the same checks on its
	 * name and type and the same clean-up if the transaction rolls back.
	 */
	public StoredFile storeBytes(byte[] content, String fileName, Long uploadedBy) {
		UploadPolicy.Checked checked = UploadPolicy.check(fileName, content.length,
				settings.uploadMaxBytes(properties.maxFileSizeMb()));
		return save(new ByteArrayInputStream(content), checked, content.length, uploadedBy);
	}

	private StoredFile save(InputStream content, UploadPolicy.Checked checked, long size, Long uploadedBy) {
		String key;
		MessageDigest digest = sha256();
		try (InputStream in = new DigestInputStream(content, digest)) {
			key = storage.put(in, checked.extension());
		}
		catch (IOException ex) {
			throw uploadFailed();
		}
		afterRollback(() -> storage.delete(key));

		StoredFile file = new StoredFile();
		file.setStorageProvider(storage.provider());
		file.setStorageKey(key);
		file.setOriginalName(checked.fileName());
		file.setContentType(checked.contentType());
		file.setSizeBytes(size);
		file.setChecksumSha256(HexFormat.of().formatHex(digest.digest()));
		file.setUploadedBy(uploadedBy);
		return repository.save(file);
	}

	private static ApiException uploadFailed() {
		return new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED",
				"The file could not be stored");
	}

	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public Resource content(StoredFile file) {
		Resource resource = storage.get(file.getStorageKey());
		if (!resource.exists()) {
			throw ApiException.notFound("FILE_MISSING", "The file is no longer available");
		}
		return resource;
	}

	public void delete(StoredFile file) {
		String key = file.getStorageKey();
		repository.delete(file);
		afterCommit(() -> storage.delete(key));
	}

	private static void afterRollback(Runnable action) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					action.run();
				}
			}
		});
	}

	private static void afterCommit(Runnable action) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
