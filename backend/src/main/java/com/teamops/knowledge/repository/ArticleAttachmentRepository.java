package com.teamops.knowledge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.knowledge.entity.ArticleAttachment;

public interface ArticleAttachmentRepository extends JpaRepository<ArticleAttachment, ArticleAttachment.Id> {

	@EntityGraph(attributePaths = { "file", "addedBy" })
	List<ArticleAttachment> findByArticleIdOrderByAddedAtAsc(Long articleId);

}
