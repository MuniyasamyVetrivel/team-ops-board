package com.teamops.auth.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A refresh-token session. Only the SHA-256 hash of the token is stored. */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpiredAt(Instant now) {
		return !expiresAt.isAfter(now);
	}

}
