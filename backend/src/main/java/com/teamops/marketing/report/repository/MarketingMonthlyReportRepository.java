package com.teamops.marketing.report.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.marketing.report.entity.MarketingMonthlyReport;

public interface MarketingMonthlyReportRepository extends JpaRepository<MarketingMonthlyReport, Long> {

	Optional<MarketingMonthlyReport> findByYearAndMonth(Integer year, Integer month);

	boolean existsByYearAndMonth(Integer year, Integer month);

	/** Frozen months, newest first. */
	List<MarketingMonthlyReport> findAllByOrderByYearDescMonthDesc();

}
