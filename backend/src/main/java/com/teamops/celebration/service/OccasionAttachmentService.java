package com.teamops.celebration.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.celebration.dto.CelebrationDtos.Attachment;
import com.teamops.celebration.entity.OccasionAttachment;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.repository.OccasionAttachmentRepository;
import com.teamops.common.exception.ApiException;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Files on important days (creatives) and events. Uploads are checked (type, size, name) and stored by
 * {@link FileService}; downloads are served as attachments. Callers check that the subject exists and may be changed.
 */
@Service
@RequiredArgsConstructor
public class OccasionAttachmentService {

	private final OccasionAttachmentRepository repository;

	private final FileService fileService;

	private final UserRepository userRepository;

	public record Download(String fileName, String contentType, long sizeBytes, Resource content) {
	}

	@Transactional(readOnly = true)
	public List<Attachment> list(OccasionKind kind, Long subjectId) {
		return repository.findBySubjectTypeAndSubjectIdOrderByAddedAtAsc(kind, subjectId)
			.stream()
			.map(Attachment::of)
			.toList();
	}

	/** @return the stored file's name, for the subject's audit entry */
	@Transactional
	public String add(OccasionKind kind, Long subjectId, MultipartFile upload, Long actorId) {
		StoredFile file = fileService.store(upload, actorId);
		OccasionAttachment attachment = new OccasionAttachment();
		attachment.setSubjectType(kind);
		attachment.setSubjectId(subjectId);
		attachment.setFile(file);
		attachment.setAddedBy(userRepository.getReferenceById(actorId));
		repository.save(attachment);
		return file.getOriginalName();
	}

	@Transactional(readOnly = true)
	public Download download(OccasionKind kind, Long subjectId, Long fileId) {
		StoredFile file = load(kind, subjectId, fileId).getFile();
		return new Download(file.getOriginalName(), file.getContentType(), file.getSizeBytes(), fileService.content(file));
	}

	/** @return the removed file's name, for the subject's audit entry */
	@Transactional
	public String delete(OccasionKind kind, Long subjectId, Long fileId) {
		OccasionAttachment attachment = load(kind, subjectId, fileId);
		repository.delete(attachment);
		fileService.delete(attachment.getFile());
		return attachment.getFile().getOriginalName();
	}

	/** Removes every file of a subject that is being deleted. */
	@Transactional
	public void deleteAll(OccasionKind kind, Long subjectId) {
		for (OccasionAttachment attachment : repository.findBySubjectTypeAndSubjectIdOrderByAddedAtAsc(kind, subjectId)) {
			repository.delete(attachment);
			fileService.delete(attachment.getFile());
		}
	}

	/** Attachment counts by subject id (subjects without files are absent). */
	@Transactional(readOnly = true)
	public Map<Long, Long> counts(OccasionKind kind, Collection<Long> subjectIds) {
		Map<Long, Long> counts = new HashMap<>();
		if (!subjectIds.isEmpty()) {
			repository.countBySubjects(kind, subjectIds).forEach(row -> counts.put((Long) row[0], (Long) row[1]));
		}
		return counts;
	}

	private OccasionAttachment load(OccasionKind kind, Long subjectId, Long fileId) {
		return repository.findBySubjectTypeAndSubjectIdAndFileId(kind, subjectId, fileId)
			.orElseThrow(() -> ApiException.notFound("ATTACHMENT_NOT_FOUND", "Attachment not found"));
	}

}
