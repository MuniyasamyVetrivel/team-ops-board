package com.teamops.vendor.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.teamops.common.persistence.SoftDeletableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** A company the business buys from. The name is unique; contacts are its children. */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "vendors")
public class Vendor extends SoftDeletableEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	/** ABN, GST or another tax ID, as the vendor writes it. */
	@Column(name = "tax_id", length = 50)
	private String taxId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "category", nullable = false, length = 32)
	private VendorCategory category = VendorCategory.OTHER;

	@Column(name = "country", length = 100)
	private String country;

	@Column(name = "address", length = 500)
	private String address;

	@Column(name = "website", length = 300)
	private String website;

	@Column(name = "payment_terms", length = 100)
	private String paymentTerms;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private VendorStatus status = VendorStatus.ACTIVE;

	/** 1 (poor) to 5 (excellent); null when not rated. */
	@Column(name = "rating")
	private Integer rating;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC, id ASC")
	private List<VendorContact> contacts = new ArrayList<>();

}
