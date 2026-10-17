package com.teamops.finance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.teamops.common.audit.AuditFinance;
import com.teamops.common.persistence.SoftDeletableEntity;
import com.teamops.department.entity.Department;
import com.teamops.user.entity.User;
import com.teamops.vendor.entity.Vendor;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A tool or licence the business pays for. The name is unique. Seats used are the assigned users; free seats,
 * utilisation, the annual cost, its AUD equivalent and the days to renewal are computed ({@code LicenceRules}).
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "finance_licences")
public class FinanceLicence extends SoftDeletableEntity {

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	/** Written through this column, so saving never clears it while the vendor is in the recycle bin. */
	@Column(name = "vendor_id")
	private Long vendorId;

	/** Read-only view of {@link #vendorId}: null while the vendor is in the recycle bin. */
	@ManyToOne(fetch = FetchType.EAGER)
	@NotFound(action = NotFoundAction.IGNORE)
	@JoinColumn(name = "vendor_id", insertable = false, updatable = false)
	private Vendor vendor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@Column(name = "seats_paid")
	private Integer seatsPaid;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "billing_cycle", nullable = false, length = 16)
	private BillingCycle billingCycle;

	@AuditFinance
	@Column(name = "cost", precision = 14, scale = 2)
	private BigDecimal cost;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "renewal_date")
	private LocalDate renewalDate;

	/** A label such as "Corporate Amex"; never a card number ({@code LicenceRules.checkPaymentMethod}). */
	@Column(name = "payment_method", length = 60)
	private String paymentMethod;

	@Column(name = "card_last4", length = 4)
	private String cardLast4;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private LicenceStatus status = LicenceStatus.ACTIVE;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@OneToMany(mappedBy = "licence", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<LicenceAssignment> assignments = new ArrayList<>();

}
