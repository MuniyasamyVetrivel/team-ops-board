package com.teamops.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
	int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);

}
