package com.teamops.backup.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.backup.entity.BackupRun;
import com.teamops.backup.entity.BackupRun.Status;

public interface BackupRunRepository extends JpaRepository<BackupRun, Long> {

	Page<BackupRun> findAllBy(Pageable pageable);

	Optional<BackupRun> findFirstByStatusOrderByStartedAtDesc(Status status);

	/** The newest finished run (success or failure). */
	Optional<BackupRun> findFirstByStatusInOrderByStartedAtDesc(List<Status> statuses);

	boolean existsByStatus(Status status);

	List<BackupRun> findByStatus(Status status);

	/** Successful backups whose file is still kept on this target, for the retention policy. */
	List<BackupRun> findByStatusAndTargetAndRemovedAtIsNull(Status status, String target);

	Optional<BackupRun> findFirstBySecondaryStatusOrderByStartedAtDesc(Status status);

	/** The newest run whose second copy finished (success or failure). */
	Optional<BackupRun> findFirstBySecondaryStatusInOrderByStartedAtDesc(List<Status> statuses);

	/** Successful second copies still kept on this target, for the retention policy. */
	List<BackupRun> findBySecondaryStatusAndSecondaryTargetAndSecondaryRemovedAtIsNull(Status status, String target);

}
