package com.teamops.document.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.teamops.common.storage.FileService;
import com.teamops.document.entity.Document;
import com.teamops.document.entity.DocumentVersion;
import com.teamops.document.repository.DocumentRepository;
import com.teamops.document.repository.DocumentVersionRepository;
import com.teamops.recyclebin.service.RecycleBinHandler;

import lombok.RequiredArgsConstructor;

/** A document in the recycle bin keeps every version and file; the permanent delete removes them. */
@Component
@RequiredArgsConstructor
class DocumentRecycleHandler implements RecycleBinHandler<Document> {

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository versionRepository;

	private final FileService fileService;

	@Override
	public Class<Document> type() {
		return Document.class;
	}

	@Override
	public void beforePurge(Document document) {
		List<DocumentVersion> versions = documentRepository.findVersions(document.getId());
		versionRepository.deleteAll(versions);
		versionRepository.flush();
		versions.forEach(version -> fileService.delete(version.getFile()));
	}

}
