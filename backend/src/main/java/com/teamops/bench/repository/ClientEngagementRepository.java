package com.teamops.bench.repository;

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

import com.teamops.bench.entity.ClientEngagement;

public interface ClientEngagementRepository
		extends JpaRepository<ClientEngagement, Long>, JpaSpecificationExecutor<ClientEngagement> {

	/** Live engagements per client, for a page of clients. */
	interface ClientCount {

		Long getClientId();

		long getTotal();

	}

	@Override
	@EntityGraph(attributePaths = { "client", "accountManager" })
	Page<ClientEngagement> findAll(Specification<ClientEngagement> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "client", "accountManager" })
	Optional<ClientEngagement> findDetailedById(Long id);

	/** Case-insensitive under the table collation, like the unique key. */
	boolean existsByClientIdAndName(Long clientId, String name);

	boolean existsByClientIdAndNameAndIdNot(Long clientId, String name, Long id);

	boolean existsByClientId(Long clientId);

	@Query("select e.client.id as clientId, count(e) as total from ClientEngagement e "
			+ "where e.client.id in :clientIds group by e.client.id")
	List<ClientCount> countByClient(@Param("clientIds") Collection<Long> clientIds);

}
