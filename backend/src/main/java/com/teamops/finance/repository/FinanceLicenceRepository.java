package com.teamops.finance.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.finance.entity.FinanceLicence;
import com.teamops.finance.entity.LicenceStatus;

public interface FinanceLicenceRepository extends JpaRepository<FinanceLicence, Long>, JpaSpecificationExecutor<FinanceLicence> {

	@EntityGraph(attributePaths = { "owner", "department" })
	@Query("select l from FinanceLicence l where l.id = :id")
	Optional<FinanceLicence> findDetailedById(@Param("id") Long id);

	/** Case-insensitive under the table collation, like the unique key. */
	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);

	/** Live licences using a vendor (they keep it from being deleted). */
	long countByVendorId(Long vendorId);

	Page<FinanceLicence> findByVendorId(Long vendorId, Pageable pageable);

	/** Licences of these statuses renewing between the two days, soonest first. */
	@Query("""
			select l from FinanceLicence l
			where l.renewalDate between :from and :to and l.status in :statuses
			order by l.renewalDate asc, l.id asc
			""")
	List<FinanceLicence> findRenewalsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("statuses") Collection<LicenceStatus> statuses);

}
