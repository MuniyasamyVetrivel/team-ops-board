package com.teamops.vendor.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.teamops.common.audit.AuditRef;
import com.teamops.common.audit.AuditedChild;
import com.teamops.common.persistence.AuditedEntity;
import com.teamops.common.storage.StoredFile;
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

/** A file on a contract (the signed contract, an order form, a renewal quote). */
@Getter
@Setter
@Entity
@Table(name = "vendor_contract_documents")
public class VendorContractDocument extends AuditedEntity implements AuditedChild {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "contract_id", nullable = false)
	private VendorContract contract;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id", nullable = false)
	private StoredFile file;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "added_by")
	private User addedBy;

	@CreationTimestamp
	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	@Override
	public AuditRef auditParent() {
		return contract == null ? null : AuditRef.of(VendorContract.class, contract.getId());
	}

}
