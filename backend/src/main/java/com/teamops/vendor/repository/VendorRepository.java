package com.teamops.vendor.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.vendor.entity.Vendor;

public interface VendorRepository extends JpaRepository<Vendor, Long>, JpaSpecificationExecutor<Vendor> {

	/** Case-insensitive under the table collation, like the unique key. */
	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);

	/** Case-insensitive under the table collation (the contract import names vendors). */
	Optional<Vendor> findByName(String name);

}
