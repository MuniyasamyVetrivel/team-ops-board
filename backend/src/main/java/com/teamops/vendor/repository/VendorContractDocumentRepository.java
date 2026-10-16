package com.teamops.vendor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.vendor.entity.VendorContractDocument;

public interface VendorContractDocumentRepository extends JpaRepository<VendorContractDocument, Long> {

	@EntityGraph(attributePaths = { "file", "addedBy" })
	List<VendorContractDocument> findByContractIdOrderByAddedAtAscIdAsc(Long contractId);

	@EntityGraph(attributePaths = "file")
	Optional<VendorContractDocument> findByContractIdAndFileId(Long contractId, Long fileId);

	long countByContractId(Long contractId);

}
