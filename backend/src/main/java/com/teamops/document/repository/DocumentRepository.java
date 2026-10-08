package com.teamops.document.repository;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

import com.teamops.document.entity.Document;
import com.teamops.document.entity.DocumentVersion;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

	@Override
	@EntityGraph(attributePaths = { "department", "project", "uploadedBy" })
	Page<Document> findAll(Specification<Document> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "department", "project", "uploadedBy" })
	Optional<Document> findDetailedById(Long id);

	/** The current version of each document, with its file (one query for a page). */
	@Query("""
			select v from DocumentVersion v join fetch v.file left join fetch v.uploadedBy
			where v.document.id in :ids and v.versionNo = v.document.currentVersionNo
			""")
	List<DocumentVersion> findCurrentVersions(@Param("ids") Collection<Long> documentIds);

	@Query("""
			select v from DocumentVersion v join fetch v.file left join fetch v.uploadedBy
			where v.document.id = :id order by v.versionNo desc
			""")
	List<DocumentVersion> findVersions(@Param("id") Long documentId);

	/** Company-wide documents, the given departments' documents, and the uploader's own. Null = no restriction. */
	static Specification<Document> visibleTo(Long userId, Set<Long> departmentIds) {
		if (departmentIds == null) {
			return (root, query, cb) -> cb.conjunction();
		}
		return (root, query, cb) -> cb.or(cb.isNull(root.get("department")),
				root.get("department").get("id").in(departmentIds), cb.equal(root.get("uploadedBy").get("id"), userId));
	}

	static Specification<Document> matches(String search) {
		if (!StringUtils.hasText(search)) {
			return (root, query, cb) -> cb.conjunction();
		}
		String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
			.replace("_", "\\_") + "%";
		return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
				cb.like(cb.lower(root.get("description")), pattern, '\\'));
	}

	static Specification<Document> inDepartment(Long departmentId) {
		return (root, query, cb) -> departmentId == null ? cb.conjunction()
				: cb.equal(root.get("department").get("id"), departmentId);
	}

	static Specification<Document> inProject(Long projectId) {
		return (root, query, cb) -> projectId == null ? cb.conjunction()
				: cb.equal(root.get("project").get("id"), projectId);
	}

}
