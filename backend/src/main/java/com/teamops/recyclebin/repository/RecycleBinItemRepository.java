package com.teamops.recyclebin.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.teamops.recyclebin.entity.RecycleBinItem;

public interface RecycleBinItemRepository extends JpaRepository<RecycleBinItem, Long>, JpaSpecificationExecutor<RecycleBinItem> {

	Optional<RecycleBinItem> findByEntityTypeAndEntityId(String entityType, Long entityId);

	/** Items past the retention period, oldest first, for the automatic permanent delete. */
	@Query("select i.id from RecycleBinItem i where i.deletedAt < :before order by i.deletedAt, i.id")
	List<Long> findIdsDeletedBefore(Instant before, Pageable page);

	/** Everyone who has deleted something still in the bin, for the "deleted by" filter. */
	@Query("select distinct i.deletedById from RecycleBinItem i where i.deletedById is not null")
	List<Long> findDeleterIds();

}
