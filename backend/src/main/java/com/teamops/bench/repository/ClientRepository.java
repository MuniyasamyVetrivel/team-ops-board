package com.teamops.bench.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.bench.entity.Client;

public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {

	/** Case-insensitive under the table collation, like the unique key. */
	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);

}
