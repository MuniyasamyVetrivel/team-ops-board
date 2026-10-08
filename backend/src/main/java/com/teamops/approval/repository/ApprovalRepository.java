package com.teamops.approval.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.approval.entity.Approval;

public interface ApprovalRepository extends JpaRepository<Approval, Long>, JpaSpecificationExecutor<Approval> {

	/** Paged search; steps are batch-loaded for the page (default_batch_fetch_size). */
	@Override
	@EntityGraph(attributePaths = { "type", "requester", "department" })
	Page<Approval> findAll(Specification<Approval> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "type", "requester", "department" })
	Optional<Approval> findDetailedById(Long id);

	/** Bounded list (calendar due dates). */
	@EntityGraph(attributePaths = { "type", "requester", "department" })
	List<Approval> findAll(Specification<Approval> spec);

}
