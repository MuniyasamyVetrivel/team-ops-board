package com.teamops.finance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.teamops.approval.entity.Approval;
import com.teamops.common.audit.AuditFinance;
import com.teamops.common.persistence.SoftDeletableEntity;
import com.teamops.common.storage.StoredFile;
import com.teamops.department.entity.Department;
import com.teamops.vendor.entity.Vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Money spent. {@code amount} is the total in its currency, GST included; {@code gst} is the GST in it. The AUD
 * equivalent is a snapshot the brief asks for: {@code audRate} (1 for AUD) is the rate in force on the expense date,
 * {@code audRateDate} the day that rate took effect and {@code amountAud} the amount at it. Later rate changes do not
 * alter it; a new date or currency takes the rate again ({@code ExpenseService}).
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "finance_expenses")
public class FinanceExpense extends SoftDeletableEntity {

	@Column(name = "expense_date", nullable = false)
	private LocalDate expenseDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "category", nullable = false, length = 32)
	private ExpenseCategory category;

	/** Written through this column, so saving never clears it while the vendor is in the recycle bin. */
	@Column(name = "vendor_id")
	private Long vendorId;

	/** Read-only view of {@link #vendorId}: null while the vendor is in the recycle bin. */
	@ManyToOne(fetch = FetchType.EAGER)
	@NotFound(action = NotFoundAction.IGNORE)
	@JoinColumn(name = "vendor_id", insertable = false, updatable = false)
	private Vendor vendor;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	@AuditFinance
	@Column(name = "amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@AuditFinance
	@Column(name = "gst", precision = 14, scale = 2)
	private BigDecimal gst;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	@Column(name = "aud_rate", nullable = false, precision = 18, scale = 8)
	private BigDecimal audRate;

	@Column(name = "aud_rate_date")
	private LocalDate audRateDate;

	@AuditFinance
	@Column(name = "amount_aud", nullable = false, precision = 14, scale = 2)
	private BigDecimal amountAud;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receipt_file_id")
	private StoredFile receipt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approval_id")
	private Approval approval;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
