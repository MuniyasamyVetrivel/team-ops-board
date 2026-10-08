package com.teamops.marketing.paid.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.marketing.paid.entity.AdDataSource;
import com.teamops.marketing.paid.entity.PaidCampaign;

public interface PaidCampaignRepository extends JpaRepository<PaidCampaign, Long>, JpaSpecificationExecutor<PaidCampaign> {

	@Override
	@EntityGraph(attributePaths = "owner")
	Page<PaidCampaign> findAll(Specification<PaidCampaign> spec, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Optional<PaidCampaign> findDetailedById(Long id);

	boolean existsByProviderAndExternalId(AdDataSource provider, String externalId);

	/** Every campaign, for matching CSV rows without a query per row. */
	List<PaidCampaign> findAllByOrderByIdAsc();

}
