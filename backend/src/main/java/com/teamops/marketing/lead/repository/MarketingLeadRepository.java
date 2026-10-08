package com.teamops.marketing.lead.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.lead.entity.LeadOrigin;
import com.teamops.marketing.lead.entity.MarketingLead;

public interface MarketingLeadRepository extends JpaRepository<MarketingLead, Long>, JpaSpecificationExecutor<MarketingLead> {

	@Override
	@EntityGraph(attributePaths = { "owner", "department", "emailCampaign", "paidCampaign", "contentItem" })
	Page<MarketingLead> findAll(Specification<MarketingLead> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "owner", "department", "emailCampaign", "paidCampaign", "contentItem", "createdBy" })
	Optional<MarketingLead> findDetailedById(Long id);

	boolean existsByEmailCampaignId(Long emailCampaignId);

	boolean existsByPaidCampaignId(Long paidCampaignId);

	boolean existsByPaidCampaignIdAndSource(Long paidCampaignId, LeadSource source);

	boolean existsByContentItemId(Long contentItemId);

	boolean existsByProviderAndExternalId(LeadOrigin provider, String externalId);

	boolean existsByEmailIgnoreCaseAndLeadDate(String email, LocalDate leadDate);

	/** The earliest lead date naming this email campaign; null when none. */
	@Query("select min(l.leadDate) from MarketingLead l where l.emailCampaign.id = :id")
	LocalDate findFirstLeadDateForEmailCampaign(@Param("id") Long emailCampaignId);

	/** The earliest lead date naming this paid campaign; null when none. */
	@Query("select min(l.leadDate) from MarketingLead l where l.paidCampaign.id = :id")
	LocalDate findFirstLeadDateForPaidCampaign(@Param("id") Long paidCampaignId);

}
