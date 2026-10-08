package com.teamops.marketing.seo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.marketing.seo.entity.KeywordRanking;

public interface KeywordRankingRepository extends JpaRepository<KeywordRanking, Long> {

	boolean existsByKeywordId(Long keywordId);

}
