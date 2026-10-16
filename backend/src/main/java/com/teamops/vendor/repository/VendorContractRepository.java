package com.teamops.vendor.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.vendor.entity.VendorContract;

public interface VendorContractRepository extends JpaRepository<VendorContract, Long>,
		JpaSpecificationExecutor<VendorContract> {

	@EntityGraph(attributePaths = "vendor")
	@Query("select c from VendorContract c where c.id = :id")
	Optional<VendorContract> findDetailedById(@Param("id") Long id);

	boolean existsByVendorId(Long vendorId);

	/** Case-insensitive under the table collation, like the unique key. */
	boolean existsByVendorIdAndTitle(Long vendorId, String title);

	boolean existsByVendorIdAndTitleAndIdNot(Long vendorId, String title, Long id);

	/** Live contracts per vendor, and the earliest date one of them not yet expired comes up for renewal. */
	interface VendorSummary {

		Long getVendorId();

		long getContracts();

		LocalDate getNextRenewal();

	}

	@Query("""
			select c.vendor.id as vendorId, count(c) as contracts,
			       min(case when coalesce(c.renewalDate, c.endDate) >= :today
			                 and (c.endDate is null or c.endDate >= :today)
			                then coalesce(c.renewalDate, c.endDate) end) as nextRenewal
			from VendorContract c where c.vendor.id in :ids group by c.vendor.id
			""")
	List<VendorSummary> summarise(@Param("ids") Collection<Long> vendorIds, @Param("today") LocalDate today);

	/**
	 * Contracts not expired by {@code today} that come up for renewal (the renewal date, or else the end date) between
	 * {@code from} and {@code to}, soonest first.
	 */
	@EntityGraph(attributePaths = "vendor")
	@Query("""
			select c from VendorContract c
			where coalesce(c.renewalDate, c.endDate) between :from and :to
			  and (c.endDate is null or c.endDate >= :from)
			order by coalesce(c.renewalDate, c.endDate) asc, c.id asc
			""")
	List<VendorContract> findRenewalsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

}
