package com.teamops.marketing.email.repository;

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

import com.teamops.marketing.email.entity.CampaignProvider;
import com.teamops.marketing.email.entity.EmailCampaign;

public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long>, JpaSpecificationExecutor<EmailCampaign> {

	@Override
	@EntityGraph(attributePaths = "owner")
	Page<EmailCampaign> findAll(Specification<EmailCampaign> spec, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Optional<EmailCampaign> findDetailedById(Long id);

	boolean existsByProviderAndExternalId(CampaignProvider provider, String externalId);

	/** Sent campaigns whose name matches, newest first: what an email lead can be linked to. */
	@Query("""
			select c from EmailCampaign c
			where c.status = com.teamops.marketing.email.entity.EmailCampaignStatus.SENT
			  and (:pattern is null or lower(c.name) like :pattern escape '!')
			order by c.campaignDate desc, c.id desc
			""")
	List<EmailCampaign> findLinkable(@Param("pattern") String pattern, Pageable pageable);
}
