package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.approval.entity.Approval;
import com.teamops.approval.repository.ApprovalRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.dto.FinanceDtos.ApprovalRef;
import com.teamops.finance.dto.FinanceDtos.ExpenseDto;
import com.teamops.finance.dto.FinanceDtos.ReceiptDto;
import com.teamops.finance.dto.FinanceDtos.SaveExpense;
import com.teamops.finance.entity.ExchangeRate;
import com.teamops.finance.entity.ExpenseCategory;
import com.teamops.finance.entity.FinanceExpense;
import com.teamops.finance.repository.FinanceExpenseRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.vendor.dto.VendorDtos.VendorRef;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorRepository;
import com.teamops.vendor.service.VendorLinkSource.Link;
import com.teamops.vendor.service.VendorLinkSource.Links;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;

/**
 * Expenses (Phase X8). Each keeps its original amount and currency plus an AUD equivalent at a stored rate (brief):
 * the rate in force on the expense date ({@link ExchangeRateService}; 400 NO_EXCHANGE_RATE without one), taken when
 * the expense is created and again when its date or currency changes. A changed amount keeps the stored rate. GST is
 * part of the amount. A vendor must be live (not newly blacklisted), the department and the Approval must exist. One
 * receipt per expense, through {@link FileService}. Changes are audited; amounts are finance amounts.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

	public static final String ENTITY = "FINANCE_EXPENSE";

	public static final int EXPORT_LIMIT = 10_000;

	/** The export's columns. */
	public static final List<String> CSV_COLUMNS = List.of("expense_date", "category", "vendor", "description",
			"amount", "gst", "currency", "aud_rate", "aud_rate_date", "amount_aud", "department", "approval",
			"receipt", "notes");

	private final FinanceExpenseRepository repository;

	/** Not VendorService: it lists the vendor link sources, one of which reads expenses through this service. */
	private final VendorRepository vendors;

	private final DepartmentRepository departments;

	private final ApprovalRepository approvals;

	private final ExchangeRateService rates;

	private final FileService fileService;

	private final AppSettingsService settings;

	private final AuditService auditService;

	private final RecycleBinService recycleBin;

	/** Both dates included; either may be open. */
	public record ExpenseFilter(String search, ExpenseCategory category, Long departmentId, Long vendorId,
			Long approvalId, LocalDate from, LocalDate to) {
	}

	public record Download(String fileName, String contentType, long sizeBytes, Resource content) {
	}

	/** The AUD rate an expense was converted at: 1 with no date for AUD. */
	record Snapshot(BigDecimal rate, LocalDate rateDate) {
	}

	@Transactional(readOnly = true)
	public PageResponse<ExpenseDto> search(ExpenseFilter filter, Pageable pageable) {
		return PageResponse.of(repository.findAll(spec(filter), pageable).map(ExpenseService::toDto));
	}

	@Transactional(readOnly = true)
	public ExpenseDto get(Long id) {
		return toDto(load(id));
	}

	@Transactional
	public ExpenseDto create(SaveExpense request, AuthenticatedUser actor, ClientInfo info) {
		FinanceExpense expense = new FinanceExpense();
		apply(expense, request);
		FinanceExpense saved = repository.saveAndFlush(expense);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("expenseDate", saved.getExpenseDate());
		details.put("category", saved.getCategory());
		details.put("description", saved.getDescription());
		details.put("amount", saved.getAmount());
		details.put("gst", saved.getGst());
		details.put("currency", saved.getCurrency());
		details.put("audRate", saved.getAudRate());
		details.put("amountAud", saved.getAmountAud());
		auditService.record(AuditAction.EXPENSE_CREATED, actor.id(), ENTITY, saved.getId(), details, info);
		return toDto(saved);
	}

	@Transactional
	public ExpenseDto update(Long id, SaveExpense request, AuthenticatedUser actor, ClientInfo info) {
		FinanceExpense expense = load(id);
		if (request.version() == null || !Objects.equals(expense.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this expense just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(expense);
		apply(expense, request);
		AuditChanges changes = new AuditChanges();
		snapshot(expense).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.EXPENSE_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), info);
		}
		return toDto(expense);
	}

	/** Moves the expense to the recycle bin; its receipt stays until the permanent delete. Audited. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo info) {
		FinanceExpense expense = load(id);
		recycleBin.moveToBin(expense, actor);
		auditService.record(AuditAction.EXPENSE_DELETED, actor.id(), ENTITY, id,
				Map.of("description", expense.getDescription(), "expenseDate", expense.getExpenseDate()), info);
	}

	// --- receipt ----------------------------------------------------------------------------------------------

	/** Attaches the receipt, replacing (and removing) any earlier one. Audited. */
	@Transactional
	public ExpenseDto attachReceipt(Long id, MultipartFile upload, AuthenticatedUser actor, ClientInfo info) {
		FinanceExpense expense = load(id);
		StoredFile previous = expense.getReceipt();
		StoredFile file = fileService.store(upload, actor.id());
		expense.setReceipt(file);
		repository.flush();
		if (previous != null) {
			fileService.delete(previous);
		}
		auditService.record(AuditAction.EXPENSE_RECEIPT_ADDED, actor.id(), ENTITY, id,
				Map.of("fileId", file.getId(), "fileName", file.getOriginalName()), info);
		return toDto(expense);
	}

	/** Always served as a download; 404 RECEIPT_NOT_FOUND without one. */
	@Transactional(readOnly = true)
	public Download receipt(Long id) {
		StoredFile file = load(id).getReceipt();
		if (file == null) {
			throw ApiException.notFound("RECEIPT_NOT_FOUND", "This expense has no receipt");
		}
		return new Download(file.getOriginalName(), file.getContentType(), file.getSizeBytes(), fileService.content(file));
	}

	@Transactional
	public ExpenseDto removeReceipt(Long id, AuthenticatedUser actor, ClientInfo info) {
		FinanceExpense expense = load(id);
		StoredFile file = expense.getReceipt();
		if (file == null) {
			throw ApiException.notFound("RECEIPT_NOT_FOUND", "This expense has no receipt");
		}
		expense.setReceipt(null);
		repository.flush();
		fileService.delete(file);
		auditService.record(AuditAction.EXPENSE_RECEIPT_REMOVED, actor.id(), ENTITY, id,
				Map.of("fileId", file.getId(), "fileName", file.getOriginalName()), info);
		return toDto(expense);
	}

	// --- vendor page and export ---------------------------------------------------------------------------------

	/** The vendor page's expenses (newest first), as links; the caller checks the viewer may see finance data. */
	@Transactional(readOnly = true)
	public Links linksForVendor(Long vendorId, int limit) {
		Page<FinanceExpense> page = repository.findByVendorId(vendorId, PageRequest.of(0, Math.max(limit, 1),
				Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id"))));
		List<Link> items = page.getContent()
			.stream()
			.map(e -> new Link(e.getId(), e.getDescription(), e.getExpenseDate() + " · " + e.getCurrency() + " "
					+ e.getAmount().toPlainString() + (FinanceModule.BASE_CURRENCY.equals(e.getCurrency()) ? ""
							: " (AUD " + e.getAmountAud().toPlainString() + ")"),
					e.getCategory().name()))
			.toList();
		return new Links(page.getTotalElements(), items);
	}

	@Transactional(readOnly = true)
	public long countUsingVendor(Long vendorId) {
		return repository.countByVendorId(vendorId);
	}

	/** CSV of the expenses the filter finds, oldest first, with the stored AUD rate and amount. */
	@Transactional(readOnly = true)
	public byte[] export(ExpenseFilter filter) {
		List<List<String>> rows = repository.findAll(spec(filter), Sort.by("expenseDate", "id"))
			.stream()
			.limit(EXPORT_LIMIT)
			.map(e -> List.of(e.getExpenseDate().toString(), e.getCategory().name(),
					e.getVendor() == null ? "" : e.getVendor().getName(), e.getDescription(), e.getAmount().toPlainString(),
					e.getGst() == null ? "" : e.getGst().toPlainString(), e.getCurrency(), e.getAudRate().toPlainString(),
					text(e.getAudRateDate()), e.getAmountAud().toPlainString(),
					e.getDepartment() == null ? "" : e.getDepartment().getCode(),
					e.getApproval() == null ? "" : e.getApproval().getCode(),
					e.getReceipt() == null ? "" : e.getReceipt().getOriginalName(), text(e.getNotes())))
			.toList();
		return CsvWriter.write(CSV_COLUMNS, rows);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void apply(FinanceExpense expense, SaveExpense request) {
		String currency = FinanceText.currency(request.currency(), settings.financeDefaultCurrency());
		if (request.gst() != null && request.gst().compareTo(request.amount()) > 0) {
			throw ApiException.badRequest("INVALID_GST", "The GST cannot be more than the amount it is part of");
		}
		Vendor vendor = null;
		if (request.vendorId() != null) {
			boolean unchanged = request.vendorId().equals(expense.getVendorId()) && expense.getVendor() != null;
			vendor = unchanged ? expense.getVendor() : vendors.findById(request.vendorId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_VENDOR", "Vendor not found"));
			if (!unchanged && vendor.getStatus() == VendorStatus.BLACKLISTED) {
				throw ApiException.badRequest("VENDOR_BLACKLISTED", vendor.getName() + " is blacklisted");
			}
		}
		Approval approval = null;
		if (request.approvalId() != null) {
			approval = expense.getApproval() != null && expense.getApproval().getId().equals(request.approvalId())
					? expense.getApproval() : approvals.findById(request.approvalId())
						.orElseThrow(() -> ApiException.badRequest("UNKNOWN_APPROVAL", "Approval not found"));
		}
		boolean newRate = expense.getId() == null || !currency.equals(expense.getCurrency())
				|| !request.expenseDate().equals(expense.getExpenseDate());
		if (newRate) {
			Snapshot snapshot = rateOn(currency, request.expenseDate());
			expense.setAudRate(snapshot.rate());
			expense.setAudRateDate(snapshot.rateDate());
		}
		BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
		expense.setExpenseDate(request.expenseDate());
		expense.setCategory(request.category());
		expense.setVendorId(request.vendorId());
		// The read-only view as well, so the saved expense reads back with its vendor in this transaction.
		expense.setVendor(vendor);
		expense.setDescription(request.description().trim());
		expense.setAmount(amount);
		expense.setGst(request.gst() == null ? null : request.gst().setScale(2, RoundingMode.UNNECESSARY));
		expense.setCurrency(currency);
		expense.setAmountAud(ExchangeRateService.toAud(amount, expense.getAudRate()));
		expense.setDepartment(request.departmentId() == null ? null : departments.findById(request.departmentId())
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found")));
		expense.setApproval(approval);
		expense.setNotes(FinanceText.trimToNull(request.notes()));
	}

	/** The rate in force on {@code date}: 1 for AUD; 400 NO_EXCHANGE_RATE when the currency has none by then. */
	Snapshot rateOn(String currency, LocalDate date) {
		if (FinanceModule.BASE_CURRENCY.equals(currency)) {
			return new Snapshot(BigDecimal.ONE.setScale(8), null);
		}
		ExchangeRate rate = rates.ratesOn(List.of(currency), date).get(currency);
		if (rate == null) {
			throw ApiException.badRequest("NO_EXCHANGE_RATE", "There is no " + currency + " rate on or before " + date
					+ ". Add one under Exchange rates first.");
		}
		return new Snapshot(rate.getRateToAud(), rate.getEffectiveFrom());
	}

	/** The fields an update audits; amounts are hidden from non-finance viewers. */
	private static Map<String, Object> snapshot(FinanceExpense e) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("expenseDate", e.getExpenseDate());
		values.put("category", e.getCategory());
		values.put("vendorId", e.getVendorId());
		values.put("description", e.getDescription());
		values.put("amount", e.getAmount());
		values.put("gst", e.getGst());
		values.put("currency", e.getCurrency());
		values.put("audRate", e.getAudRate());
		values.put("amountAud", e.getAmountAud());
		values.put("departmentId", e.getDepartment() == null ? null : e.getDepartment().getId());
		values.put("approvalId", e.getApproval() == null ? null : e.getApproval().getId());
		values.put("notes", e.getNotes());
		return values;
	}

	static ExpenseDto toDto(FinanceExpense e) {
		BigDecimal gst = e.getGst();
		StoredFile receipt = e.getReceipt();
		Approval approval = e.getApproval();
		return new ExpenseDto(e.getId(), e.getExpenseDate(), e.getCategory(), e.getVendorId(), VendorRef.of(e.getVendor()),
				e.getDescription(), e.getAmount(), gst, gst == null ? e.getAmount() : e.getAmount().subtract(gst),
				e.getCurrency(), e.getAudRate(), e.getAudRateDate(), e.getAmountAud(),
				gst == null ? null : ExchangeRateService.toAud(gst, e.getAudRate()),
				e.getDepartment() == null ? null : DepartmentSummary.of(e.getDepartment()),
				receipt == null ? null : new ReceiptDto(receipt.getId(), receipt.getOriginalName(), receipt.getContentType(),
						receipt.getSizeBytes(), receipt.getCreatedAt()),
				approval == null ? null : new ApprovalRef(approval.getId(), approval.getCode(), approval.getTitle(),
						approval.getStatus().name()),
				e.getNotes(), e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private FinanceExpense load(Long id) {
		return repository.findDetailedById(id).orElseThrow(() -> ApiException.notFound("EXPENSE_NOT_FOUND", "Expense not found"));
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

	static Specification<FinanceExpense> spec(ExpenseFilter f) {
		Specification<FinanceExpense> spec = (root, query, cb) -> cb.conjunction();
		if (FinanceText.trimToNull(f.search()) != null) {
			String pattern = FinanceText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> {
				var vendor = root.join("vendor", JoinType.LEFT);
				return cb.or(cb.like(cb.lower(root.get("description")), pattern, '\\'),
						cb.like(cb.lower(vendor.get("name")), pattern, '\\'),
						cb.like(cb.lower(root.get("notes")), pattern, '\\'));
			});
		}
		if (f.category() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), f.category()));
		}
		if (f.departmentId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("department").get("id"), f.departmentId()));
		}
		if (f.vendorId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("vendorId"), f.vendorId()));
		}
		if (f.approvalId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("approval").get("id"), f.approvalId()));
		}
		if (f.from() != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("expenseDate"), f.from()));
		}
		if (f.to() != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("expenseDate"), f.to()));
		}
		return spec;
	}

}
