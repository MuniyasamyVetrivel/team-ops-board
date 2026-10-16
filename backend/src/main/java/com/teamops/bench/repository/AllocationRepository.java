package com.teamops.bench.repository;

import java.time.LocalDate;
import java.util.Collection;
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

import com.teamops.bench.entity.Allocation;

public interface AllocationRepository extends JpaRepository<Allocation, Long>, JpaSpecificationExecutor<Allocation> {

	/** One allocation as the bench arithmetic needs it (no entity loading). */
	interface Slice {

		Long getId();

		Long getConsultantId();

		LocalDate getStartDate();

		LocalDate getEndDate();

		int getAllocationPercent();

		boolean isBillable();

	}

	/** A consultant's total percentage on a day. */
	interface ConsultantTotal {

		Long getConsultantId();

		long getTotal();

	}

	@Override
	@EntityGraph(attributePaths = { "consultant", "engagement", "engagement.client" })
	Page<Allocation> findAll(Specification<Allocation> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "consultant", "engagement", "engagement.client" })
	Optional<Allocation> findDetailedById(Long id);

	boolean existsByConsultantId(Long consultantId);

	boolean existsByEngagementId(Long engagementId);

	@Query("select a.id as id, a.consultant.id as consultantId, a.startDate as startDate, a.endDate as endDate, "
			+ "a.allocationPercent as allocationPercent, a.billable as billable from Allocation a "
			+ "where a.consultant.id in :consultantIds")
	List<Slice> findSlices(@Param("consultantIds") Collection<Long> consultantIds);

	@Query("select a.consultant.id as consultantId, sum(a.allocationPercent) as total from Allocation a "
			+ "where a.consultant.id in :consultantIds and a.startDate <= :day "
			+ "and (a.endDate is null or a.endDate >= :day) group by a.consultant.id")
	List<ConsultantTotal> totalsOn(@Param("consultantIds") Collection<Long> consultantIds, @Param("day") LocalDate day);

	/** Allocations ending between the two days (both included), soonest first: the roll-offs. */
	@EntityGraph(attributePaths = { "consultant", "engagement", "engagement.client" })
	List<Allocation> findByEndDateBetweenOrderByEndDateAscIdAsc(LocalDate from, LocalDate to);

}
