package com.teamops.notification.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findByUserId(Long userId, Pageable pageable);

	Page<Notification> findByUserIdAndReadAtIsNull(Long userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(Long userId);

	Optional<Notification> findByIdAndUserId(Long id, Long userId);

	@Modifying
	@Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
	int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

	/** Reminder keys that were already sent, so the scheduler stays idempotent. */
	@Query("select n.dedupKey from Notification n where n.dedupKey in :keys")
	Set<String> findExistingDedupKeys(@Param("keys") Collection<String> keys);

}
