package com.teamops.marketing.activity.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.marketing.activity.entity.MarketingActivity;

public interface MarketingActivityRepository
		extends JpaRepository<MarketingActivity, Long>, JpaSpecificationExecutor<MarketingActivity> {

	@Override
	@EntityGraph(attributePaths = { "department", "owner", "defaultAssignee" })
	Page<MarketingActivity> findAll(Specification<MarketingActivity> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "department", "owner", "defaultAssignee" })
	Optional<MarketingActivity> findDetailedById(Long id);

	/** Activities the daily job generates occurrences for. */
	@EntityGraph(attributePaths = { "department", "owner", "defaultAssignee" })
	List<MarketingActivity> findByActiveTrue();

}
