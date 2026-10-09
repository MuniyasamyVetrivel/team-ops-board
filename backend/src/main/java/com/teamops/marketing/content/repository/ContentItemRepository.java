package com.teamops.marketing.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.marketing.content.entity.ContentItem;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long>, JpaSpecificationExecutor<ContentItem> {

	@Override
	@EntityGraph(attributePaths = { "author", "owner", "targetKeyword", "targetPage" })
	Page<ContentItem> findAll(Specification<ContentItem> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "author", "owner", "targetKeyword", "targetPage", "createdBy" })
	Optional<ContentItem> findDetailedById(Long id);

	/** Live (published or updated) content whose title matches, newest first: what a lead can be linked to. */
	@Query("""
			select c from ContentItem c
			where c.status in (com.teamops.marketing.content.entity.ContentStatus.PUBLISHED,
			                   com.teamops.marketing.content.entity.ContentStatus.UPDATED)
			  and (:pattern is null or lower(c.title) like :pattern escape '!')
			order by c.publicationDate desc, c.id desc
			""")
	List<ContentItem> findLinkable(@Param("pattern") String pattern, Pageable pageable);

	/** Another item with this URL (ignoring case); {@code exceptId} may be null. */
	@Query("select count(c) > 0 from ContentItem c where lower(c.url) = lower(:url) and (:exceptId is null or c.id <> :exceptId)")
	boolean existsUrl(@Param("url") String url, @Param("exceptId") Long exceptId);

}
