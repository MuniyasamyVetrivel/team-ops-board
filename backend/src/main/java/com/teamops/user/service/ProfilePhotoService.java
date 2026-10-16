package com.teamops.user.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.common.storage.StoredFileRepository;
import com.teamops.common.web.ClientInfo;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Profile photos (Phase X4, for everyone): each user can change or remove their own photo; only a Super Admin can
 * change anyone else's (403 otherwise); every signed-in user can see them. The upload is checked and turned into a
 * square JPEG by {@link ProfilePhotoImages} and kept with the other files ({@link FileService}); the previous photo
 * is deleted once the change commits. Every change is audited (USER_PHOTO_CHANGED).
 */
@Service
@RequiredArgsConstructor
public class ProfilePhotoService {

	private final UserRepository users;

	private final FileService fileService;

	private final StoredFileRepository storedFiles;

	private final AuditService auditService;

	/** The stored photo: its bytes and version (the file id, which changes with every new photo). */
	public record Photo(long version, long sizeBytes, Resource content) {

	}

	@Transactional
	public UserSummary setPhoto(Long userId, MultipartFile upload, AuthenticatedUser actor, ClientInfo client) {
		User user = loadEditable(userId, actor);
		if (upload == null || upload.isEmpty()) {
			throw ApiException.badRequest("PHOTO_EMPTY", "Choose a photo to upload");
		}
		if (upload.getSize() > ProfilePhotoImages.MAX_UPLOAD_BYTES) {
			throw ApiException.badRequest("PHOTO_TOO_LARGE", "The photo can be at most 2 MB");
		}
		byte[] jpeg;
		try {
			jpeg = ProfilePhotoImages.toSquareJpeg(upload.getBytes());
		}
		catch (IOException ex) {
			throw ApiException.badRequest("PHOTO_UNREADABLE", "The photo could not be read");
		}
		StoredFile photo = fileService.storeBytes(jpeg, "profile-photo-" + user.getId() + ".jpg", actor.id());
		Long previous = user.getPhotoFileId();
		// Audit before the user row is written: the audit runs in its own transaction and refers to the user, so it
		// must not wait for this transaction's lock on that row (a user changing their own photo).
		audit(user, previous == null ? "added" : "replaced", actor, client);
		user.setPhotoFileId(photo.getId());
		if (previous != null) {
			// Flushed after the user update, so the row no longer points at the file when it goes.
			storedFiles.findById(previous).ifPresent(fileService::delete);
		}
		return UserSummary.of(user);
	}

	@Transactional
	public UserSummary removePhoto(Long userId, AuthenticatedUser actor, ClientInfo client) {
		User user = loadEditable(userId, actor);
		Long previous = user.getPhotoFileId();
		if (previous != null) {
			audit(user, "removed", actor, client);
			user.setPhotoFileId(null);
			storedFiles.findById(previous).ifPresent(fileService::delete);
		}
		return UserSummary.of(user);
	}

	/** Any signed-in user may see anyone's photo. 404 {@code PHOTO_NOT_FOUND} without one. */
	@Transactional(readOnly = true)
	public Photo photo(Long userId) {
		User user = users.findById(userId)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
		StoredFile file = user.getPhotoFileId() == null ? null : storedFiles.findById(user.getPhotoFileId()).orElse(null);
		if (file == null) {
			throw ApiException.notFound("PHOTO_NOT_FOUND", "This user has no photo");
		}
		return new Photo(file.getId(), file.getSizeBytes(), fileService.content(file));
	}

	private User loadEditable(Long userId, AuthenticatedUser actor) {
		if (!actor.id().equals(userId) && !actor.isSuperAdmin()) {
			throw ApiException.forbidden("PHOTO_FORBIDDEN", "Only a Super Admin can change someone else's photo");
		}
		return users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
	}

	private void audit(User user, String change, AuthenticatedUser actor, ClientInfo client) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("change", change);
		details.put("email", user.getEmail());
		details.put("self", actor.id().equals(user.getId()));
		auditService.record(AuditAction.USER_PHOTO_CHANGED, actor.id(), "USER", user.getId(), details, client);
	}

}
