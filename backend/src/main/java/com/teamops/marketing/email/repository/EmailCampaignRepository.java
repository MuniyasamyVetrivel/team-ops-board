package com.teamops.marketing.email.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.marketing.email.entity.CampaignProvider;
import com.teamops.marketing.email.entity.EmailCampaign;

public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long>, JpaSpecificationExecutor<EmailCampaign> {

	@Override
	@EntityGraph(attributePaths = "owner")
	Page<EmailCampaign> findAll(Specification<EmailCampaign> spec, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Optional<EmailCampaign> findDetailedById(Long id);

	boolean existsByProviderAndExternalId(CampaignProvider provider, String externalId);

}
