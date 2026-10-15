package com.teamops.common.audit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Audit entries can be added and read, never changed or removed: this repository deliberately exposes no update or
 * delete method (the methods below are served by Spring Data's standard implementation).
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

	AuditLog save(AuditLog log);

	Optional<AuditLog> findById(Long id);

	Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);

	long count(Specification<AuditLog> spec);

	/** Entity types that appear in the log, for the viewer's filter. */
	@Query("select distinct a.entityType from AuditLog a where a.entityType is not null order by a.entityType")
	List<String> findEntityTypes();

	/** Everyone who has an entry, for the viewer's actor filter. */
	@Query("select distinct a.actorId from AuditLog a where a.actorId is not null")
	List<Long> findActorIds();

}
