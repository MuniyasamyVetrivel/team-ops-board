package com.teamops.vendor.service;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.exception.ApiException;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.user.repository.UserRepository;
import com.teamops.vendor.dto.VendorDtos.DocumentDto;
import com.teamops.vendor.entity.VendorContract;
import com.teamops.vendor.entity.VendorContractDocument;
import com.teamops.vendor.repository.VendorContractDocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Files on vendor contracts. Uploads are checked (type, size, name) and stored by {@link FileService}; downloads are
 * served as attachments. Callers check that the contract exists and may be changed, and audit the change.
 */
@Service
@RequiredArgsConstructor
public class ContractDocumentService {

	public static final int MAX_DOCUMENTS = 20;

	private final VendorContractDocumentRepository repository;

	private final FileService fileService;

	private final UserRepository userRepository;

	public record Download(String fileName, String contentType, long sizeBytes, Resource content) {
	}

	@Transactional(readOnly = true)
	public List<DocumentDto> list(Long contractId) {
		return repository.findByContractIdOrderByAddedAtAscIdAsc(contractId).stream().map(DocumentDto::of).toList();
	}

	/** Stores the upload on the contract; 400 TOO_MANY_DOCUMENTS beyond {@link #MAX_DOCUMENTS}. */
	@Transactional
	public StoredFile add(VendorContract contract, MultipartFile upload, Long actorId) {
		if (repository.countByContractId(contract.getId()) >= MAX_DOCUMENTS) {
			throw ApiException.badRequest("TOO_MANY_DOCUMENTS",
					"A contract can have at most " + MAX_DOCUMENTS + " documents. Remove one first.");
		}
		StoredFile file = fileService.store(upload, actorId);
		VendorContractDocument document = new VendorContractDocument();
		document.setContract(contract);
		document.setFile(file);
		document.setAddedBy(userRepository.getReferenceById(actorId));
		repository.save(document);
		return file;
	}

	@Transactional(readOnly = true)
	public Download download(Long contractId, Long fileId) {
		StoredFile file = load(contractId, fileId).getFile();
		return new Download(file.getOriginalName(), file.getContentType(), file.getSizeBytes(), fileService.content(file));
	}

	/** @return the removed file, for the contract's audit entry */
	@Transactional
	public StoredFile remove(Long contractId, Long fileId) {
		VendorContractDocument document = load(contractId, fileId);
		repository.delete(document);
		fileService.delete(document.getFile());
		return document.getFile();
	}

	/** Removes every document of a contract that is being purged, stored files included. */
	@Transactional
	public void removeAll(Long contractId) {
		for (VendorContractDocument document : repository.findByContractIdOrderByAddedAtAscIdAsc(contractId)) {
			repository.delete(document);
			fileService.delete(document.getFile());
		}
	}

	private VendorContractDocument load(Long contractId, Long fileId) {
		return repository.findByContractIdAndFileId(contractId, fileId)
			.orElseThrow(() -> ApiException.notFound("DOCUMENT_NOT_FOUND", "Document not found"));
	}

}
