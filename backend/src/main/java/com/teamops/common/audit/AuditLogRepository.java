package com.teamops.common.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

	/** Entity types that appear in the log, for the viewer's filter. */
	@Query("select distinct a.entityType from AuditLog a where a.entityType is not null order by a.entityType")
	List<String> findEntityTypes();

	/** Everyone who has an entry, for the viewer's actor filter. */
	@Query("select distinct a.actorId from AuditLog a where a.actorId is not null")
	List<Long> findActorIds();

}
