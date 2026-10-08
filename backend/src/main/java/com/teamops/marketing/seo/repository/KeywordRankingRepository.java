package com.teamops.marketing.seo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.marketing.seo.entity.KeywordRanking;

public interface KeywordRankingRepository extends JpaRepository<KeywordRanking, Long> {

	boolean existsByKeywordId(Long keywordId);

	Optional<KeywordRanking> findByKeywordIdAndMonthAndYear(Long keywordId, Integer month, Integer year);

	/** A keyword's whole history, newest month first. */
	@EntityGraph(attributePaths = "recordedBy")
	List<KeywordRanking> findByKeywordIdOrderByYearDescMonthDesc(Long keywordId);

	@EntityGraph(attributePaths = { "keyword", "keyword.page", "recordedBy" })
	Optional<KeywordRanking> findDetailedById(Long id);

	/** Keyword ids (of the given ones) that already have a row for the month. */
	@Query("select r.keyword.id from KeywordRanking r where r.keyword.id in :keywordIds and r.month = :month "
			+ "and r.year = :year")
	List<Long> findRecordedKeywordIds(@Param("keywordIds") List<Long> keywordIds, @Param("month") Integer month,
			@Param("year") Integer year);

}
