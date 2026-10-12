package com.teamops.celebration.repository;

import java.time.Instant;
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

import com.teamops.celebration.entity.MarketingEvent;

public interface MarketingEventRepository extends JpaRepository<MarketingEvent, Long>, JpaSpecificationExecutor<MarketingEvent> {

	@Override
	@EntityGraph(attributePaths = { "owner", "emailCampaign" })
	Page<MarketingEvent> findAll(Specification<MarketingEvent> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "owner", "emailCampaign" })
	Optional<MarketingEvent> findDetailedById(Long id);

	/** Events starting in [from, to), earliest first. */
	@EntityGraph(attributePaths = { "owner", "emailCampaign" })
	@Query("select e from MarketingEvent e where e.startAt >= :from and e.startAt < :to order by e.startAt, e.id")
	List<MarketingEvent> findStartingBetween(@Param("from") Instant from, @Param("to") Instant to);

	boolean existsByTitle(String title);

}
