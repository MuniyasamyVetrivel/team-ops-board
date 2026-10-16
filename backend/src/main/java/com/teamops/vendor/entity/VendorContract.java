package com.teamops.vendor.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLRestriction;

import com.teamops.common.audit.AuditFinance;
import com.teamops.common.persistence.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A contract with a vendor. The title is unique per vendor. Whether it is upcoming, active or expired, when it comes up
 * for renewal and the last day to give notice are computed ({@code ContractRules}), never stored. Documents stay with
 * the contract in the recycle bin.
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "vendor_contracts")
public class VendorContract extends SoftDeletableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_id", nullable = false)
	private Vendor vendor;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Open-ended when null. */
	@Column(name = "end_date")
	private LocalDate endDate;

	@AuditFinance
	@Column(name = "value", precision = 14, scale = 2)
	private BigDecimal value;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	@Column(name = "renewal_date")
	private LocalDate renewalDate;

	@Column(name = "auto_renew", nullable = false)
	private boolean autoRenew;

	/** Days of notice the vendor needs before the renewal date. */
	@Column(name = "notice_period_days")
	private Integer noticePeriodDays;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	/** Read-only here: documents are added and removed through {@code ContractDocumentService}. */
	@OneToMany(mappedBy = "contract")
	@OrderBy("addedAt ASC, id ASC")
	private List<VendorContractDocument> documents = new ArrayList<>();

}
