package com.teamops.marketing.content.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.marketing.content.entity.ContentItem;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {

	/** Live (published or updated) content whose title matches, newest first: what a lead can be linked to. */
	@Query("""
			select c from ContentItem c
			where c.status in (com.teamops.marketing.content.entity.ContentStatus.PUBLISHED,
			                   com.teamops.marketing.content.entity.ContentStatus.UPDATED)
			  and (:pattern is null or lower(c.title) like :pattern escape '!')
			order by c.publicationDate desc, c.id desc
			""")
	List<ContentItem> findLinkable(@Param("pattern") String pattern, Pageable pageable);

}
