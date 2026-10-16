package com.teamops.bench.entity;

import java.time.LocalDate;

import com.teamops.common.audit.AuditRef;
import com.teamops.common.audit.AuditedChild;
import com.teamops.common.persistence.AuditedEntity;

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

/** A certification a consultant holds. Whether it has expired follows from {@code expiresOn} and is never stored. */
@Getter
@Setter
@Entity
@Table(name = "bench_consultant_certifications")
public class ConsultantCertification extends AuditedEntity implements AuditedChild {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "consultant_id", nullable = false)
	private Consultant consultant;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "issuer", length = 150)
	private String issuer;

	@Column(name = "issued_on")
	private LocalDate issuedOn;

	@Column(name = "expires_on")
	private LocalDate expiresOn;

	@Column(name = "credential_id", length = 100)
	private String credentialId;

	@Column(name = "position", nullable = false)
	private int position;

	@Override
	public AuditRef auditParent() {
		return consultant == null ? null : AuditRef.of(Consultant.class, consultant.getId());
	}

}
