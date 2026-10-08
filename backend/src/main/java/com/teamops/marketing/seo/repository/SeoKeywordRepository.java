package com.teamops.marketing.seo.repository;

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

import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.SearchEngine;
import com.teamops.marketing.seo.entity.SeoKeyword;

public interface SeoKeywordRepository extends JpaRepository<SeoKeyword, Long>, JpaSpecificationExecutor<SeoKeyword> {

	@Override
	@EntityGraph(attributePaths = { "page", "owner" })
	Page<SeoKeyword> findAll(Specification<SeoKeyword> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "page", "owner" })
	Optional<SeoKeyword> findDetailedById(Long id);

	long countByPageId(Long pageId);

	/** The keyword's identity: page + keyword + engine + location + device (case-insensitive, like the unique key). */
	@Query("select count(k) > 0 from SeoKeyword k where k.page.id = :pageId and k.keyword = :keyword "
			+ "and k.searchEngine = :engine and k.location = :location and k.device = :device "
			+ "and (:excludeId is null or k.id <> :excludeId)")
	boolean existsIdentity(@Param("pageId") Long pageId, @Param("keyword") String keyword,
			@Param("engine") SearchEngine engine, @Param("location") String location, @Param("device") Device device,
			@Param("excludeId") Long excludeId);

	/**
	 * Re-reads the cached positions from the keyword's two most recent history rows. Call after any change to the
	 * keyword's history; the history stays the source of truth.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update marketing_keywords k set
			  current_position = (select h.ranking_position from keyword_ranking_history h where h.keyword_id = k.id
			                      order by h.ranking_year desc, h.ranking_month desc limit 1),
			  previous_position = (select h.ranking_position from keyword_ranking_history h where h.keyword_id = k.id
			                       order by h.ranking_year desc, h.ranking_month desc limit 1 offset 1),
			  last_ranked_at = (select max(h.updated_at) from keyword_ranking_history h where h.keyword_id = k.id)
			where k.id = :id
			""", nativeQuery = true)
	int refreshRankingCache(@Param("id") Long id);

}
