package com.teamops.vendor.entity;

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

/** A person at a vendor. The first contact (position 0) is the main one. */
@Getter
@Setter
@Entity
@Table(name = "vendor_contacts")
public class VendorContact extends AuditedEntity implements AuditedChild {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_id", nullable = false)
	private Vendor vendor;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "role", length = 100)
	private String role;

	@Column(name = "email", length = 254)
	private String email;

	@Column(name = "phone", length = 50)
	private String phone;

	@Column(name = "position", nullable = false)
	private int position;

	@Override
	public AuditRef auditParent() {
		return vendor == null ? null : AuditRef.of(Vendor.class, vendor.getId());
	}

}
