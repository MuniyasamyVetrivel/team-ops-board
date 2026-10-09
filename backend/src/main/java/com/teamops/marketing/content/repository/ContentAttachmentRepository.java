package com.teamops.marketing.content.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.marketing.content.entity.ContentAttachment;

public interface ContentAttachmentRepository extends JpaRepository<ContentAttachment, ContentAttachment.Id> {

	@EntityGraph(attributePaths = { "file", "addedBy" })
	List<ContentAttachment> findByContentItemIdOrderByAddedAtAsc(Long contentItemId);

	/** Attachment counts per item, for a page of the list: rows of [content item id, count]. */
	@Query("select a.contentItem.id, count(a) from ContentAttachment a where a.contentItem.id in :ids group by a.contentItem.id")
	List<Object[]> countByContentItemIds(@Param("ids") Collection<Long> ids);

}
