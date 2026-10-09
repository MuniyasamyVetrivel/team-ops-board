package com.teamops.marketing.backlink.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.marketing.backlink.entity.Backlink;

public interface BacklinkRepository extends JpaRepository<Backlink, Long>, JpaSpecificationExecutor<Backlink> {

	@Override
	@EntityGraph(attributePaths = { "owner", "targetPage" })
	Page<Backlink> findAll(Specification<Backlink> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "owner", "targetPage", "createdBy" })
	Optional<Backlink> findDetailedById(Long id);

	/** Another backlink from the same referring page to the same target (ignoring case); {@code exceptId} may be null. */
	@Query("""
			select count(b) > 0 from Backlink b where lower(b.linkUrl) = lower(:linkUrl)
			  and lower(b.targetUrl) = lower(:targetUrl) and (:exceptId is null or b.id <> :exceptId)
			""")
	boolean existsDuplicate(@Param("linkUrl") String linkUrl, @Param("targetUrl") String targetUrl,
			@Param("exceptId") Long exceptId);

}
