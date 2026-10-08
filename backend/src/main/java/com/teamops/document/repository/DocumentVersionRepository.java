package com.teamops.document.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.document.entity.DocumentVersion;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

	@EntityGraph(attributePaths = "file")
	Optional<DocumentVersion> findByDocumentIdAndVersionNo(Long documentId, int versionNo);

}
