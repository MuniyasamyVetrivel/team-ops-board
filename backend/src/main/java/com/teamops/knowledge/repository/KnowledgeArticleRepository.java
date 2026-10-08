package com.teamops.knowledge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.knowledge.entity.KnowledgeArticle;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long>,
		JpaSpecificationExecutor<KnowledgeArticle> {

	@Override
	@EntityGraph(attributePaths = { "category", "author" })
	Page<KnowledgeArticle> findAll(Specification<KnowledgeArticle> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "category", "author", "department" })
	Optional<KnowledgeArticle> findBySlug(String slug);

	@EntityGraph(attributePaths = { "category", "author", "department" })
	Optional<KnowledgeArticle> findDetailedById(Long id);

	boolean existsBySlug(String slug);

	/**
	 * Full-text matches (InnoDB FULLTEXT on title + body, boolean mode), best first. {@code query} must already be a
	 * safe boolean-mode expression (see {@code KnowledgeSearch}).
	 */
	@Query(value = """
			select a.id from knowledge_articles a
			where match(a.title, a.body) against (:query in boolean mode)
			order by match(a.title, a.body) against (:query in boolean mode) desc
			limit 500
			""", nativeQuery = true)
	List<Long> fullTextIds(@Param("query") String query);

	@Modifying
	@Query("update KnowledgeArticle a set a.viewCount = a.viewCount + 1 where a.id = :id")
	void incrementViews(@Param("id") Long id);

	/** Published articles per category. */
	@Query("""
			select a.category.id as categoryId, count(a) as total from KnowledgeArticle a
			where a.status = com.teamops.knowledge.entity.ArticleStatus.PUBLISHED group by a.category.id
			""")
	List<CategoryCount> countPublishedByCategory();

	interface CategoryCount {

		Long getCategoryId();

		long getTotal();

	}

}
