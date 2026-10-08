package com.teamops.marketing.paid.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.marketing.paid.entity.PaidCampaignMonth;

public interface PaidCampaignMonthRepository extends JpaRepository<PaidCampaignMonth, Long> {

	Optional<PaidCampaignMonth> findByCampaignIdAndMonthAndYear(Long campaignId, Integer month, Integer year);

	/** A campaign's months, oldest first. */
	@EntityGraph(attributePaths = "recordedBy")
	List<PaidCampaignMonth> findByCampaignIdOrderByYearAscMonthAsc(Long campaignId);

	boolean existsByCampaignId(Long campaignId);

	/** The earliest and latest recorded month of a campaign ({@code year × 12 + month}); null when none. */
	@Query("select min(m.year * 12 + m.month), max(m.year * 12 + m.month) from PaidCampaignMonth m where m.campaign.id = :id")
	List<Object[]> findMonthRange(@Param("id") Long campaignId);

}
