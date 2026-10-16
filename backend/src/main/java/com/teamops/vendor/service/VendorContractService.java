package com.teamops.vendor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.StoredFile;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.vendor.dto.VendorDtos.ContractDto;
import com.teamops.vendor.dto.VendorDtos.DocumentDto;
import com.teamops.vendor.dto.VendorDtos.SaveContract;
import com.teamops.vendor.dto.VendorDtos.VendorRef;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorContract;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorContractRepository;
import com.teamops.vendor.repository.VendorRepository;
import com.teamops.vendor.service.ContractDocumentService.Download;
import com.teamops.vendor.service.ContractRules.ContractStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/**
 * Vendor contracts and their documents (Phase X7). The title is unique per vendor, including contracts in the
 * recycle bin. Status, the renewal date and the notice deadline are computed on today's business date
 * ({@link ContractRules}). A blacklisted vendor takes no new contracts. Changes are audited; the value is a finance
 * field, so audit history shows it only to FINANCE_VIEW holders.
 */
@Service
@RequiredArgsConstructor
public class VendorContractService {

	public static final String ENTITY = "VENDOR_CONTRACT";

	public static final int EXPORT_LIMIT = 10_000;

	/** The export's columns: the same as the import's. */
	public static final List<String> CSV_COLUMNS = List.of("vendor", "title", "start_date", "end_date", "value",
			"currency", "renewal_date", "auto_renew", "notice_period_days", "notes");

	private final VendorContractRepository repository;

	private final VendorRepository vendors;

	private final ContractDocumentService documents;

	private final AuditService auditService;

	private final RecycleBinService recycleBin;

	private final BusinessCalendar calendar;

	/** @param renewalWithinDays only contracts not expired that come up for renewal within this many days from today */
	public record ContractFilter(String search, Long vendorId, ContractStatus status, Integer renewalWithinDays,
			Boolean autoRenew) {
	}

	@Transactional(readOnly = true)
	public PageResponse<ContractDto> search(ContractFilter filter, Pageable pageable) {
		LocalDate today = calendar.today();
		return PageResponse.of(repository.findAll(spec(filter, today), pageable).map(c -> toDto(c, today)));
	}

	@Transactional(readOnly = true)
	public ContractDto get(Long id) {
		return toDto(load(id), calendar.today());
	}

	@Transactional
	public ContractDto create(SaveContract request, AuthenticatedUser actor, ClientInfo info) {
		VendorContract contract = new VendorContract();
		apply(contract, request);
		VendorContract saved = repository.saveAndFlush(contract);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("vendorId", saved.getVendor().getId());
		details.put("vendor", saved.getVendor().getName());
		details.put("title", saved.getTitle());
		details.put("value", saved.getValue());
		details.put("currency", saved.getCurrency());
		details.put("renewsOn", ContractRules.renewsOn(saved));
		auditService.record(AuditAction.VENDOR_CONTRACT_CREATED, actor.id(), ENTITY, saved.getId(), details, info);
		return toDto(saved, calendar.today());
	}

	@Transactional
	public ContractDto update(Long id, SaveContract request, AuthenticatedUser actor, ClientInfo info) {
		VendorContract contract = load(id);
		if (request.version() == null || !Objects.equals(contract.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this contract just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(contract);
		apply(contract, request);
		AuditChanges changes = new AuditChanges();
		snapshot(contract).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.VENDOR_CONTRACT_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), info);
		}
		return toDto(contract, calendar.today());
	}

	/** Moves the contract to the recycle bin; its documents stay with it until it is purged. Audited. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo info) {
		VendorContract contract = load(id);
		recycleBin.moveToBin(contract, actor);
		auditService.record(AuditAction.VENDOR_CONTRACT_DELETED, actor.id(), ENTITY, id,
				Map.of("vendor", contract.getVendor().getName(), "title", contract.getTitle()), info);
	}

	/** CSV in the import's columns. */
	@Transactional(readOnly = true)
	public byte[] export(ContractFilter filter) {
		List<List<String>> rows = repository
			.findAll(spec(filter, calendar.today()), Sort.by("vendor.name", "title", "id"))
			.stream()
			.limit(EXPORT_LIMIT)
			.map(c -> List.of(c.getVendor().getName(), c.getTitle(), text(c.getStartDate()), text(c.getEndDate()),
					c.getValue() == null ? "" : c.getValue().toPlainString(), c.getCurrency(), text(c.getRenewalDate()),
					c.isAutoRenew() ? "yes" : "no", text(c.getNoticePeriodDays()), text(c.getNotes())))
			.toList();
		return CsvWriter.write(CSV_COLUMNS, rows);
	}

	// --- documents --------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DocumentDto> documents(Long id) {
		load(id);
		return documents.list(id);
	}

	@Transactional
	public List<DocumentDto> addDocument(Long id, MultipartFile upload, AuthenticatedUser actor, ClientInfo info) {
		VendorContract contract = load(id);
		StoredFile file = documents.add(contract, upload, actor.id());
		auditService.record(AuditAction.VENDOR_CONTRACT_DOCUMENT_ADDED, actor.id(), ENTITY, id,
				Map.of("fileId", file.getId(), "fileName", file.getOriginalName()), info);
		return documents.list(id);
	}

	@Transactional(readOnly = true)
	public Download download(Long id, Long fileId) {
		load(id);
		return documents.download(id, fileId);
	}

	@Transactional
	public List<DocumentDto> removeDocument(Long id, Long fileId, AuthenticatedUser actor, ClientInfo info) {
		load(id);
		StoredFile file = documents.remove(id, fileId);
		auditService.record(AuditAction.VENDOR_CONTRACT_DOCUMENT_REMOVED, actor.id(), ENTITY, id,
				Map.of("fileId", fileId, "fileName", file.getOriginalName()), info);
		return documents.list(id);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void apply(VendorContract contract, SaveContract request) {
		Vendor vendor = vendors.findById(request.vendorId())
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_VENDOR", "Vendor not found"));
		boolean newVendor = contract.getVendor() == null || !contract.getVendor().getId().equals(vendor.getId());
		if (newVendor && vendor.getStatus() == VendorStatus.BLACKLISTED) {
			throw ApiException.badRequest("VENDOR_BLACKLISTED", vendor.getName() + " is blacklisted and takes no new contracts");
		}
		ContractRules.checkDates(request.startDate(), request.endDate(), request.renewalDate());
		String title = request.title().trim();
		boolean taken = contract.getId() == null ? repository.existsByVendorIdAndTitle(vendor.getId(), title)
				: repository.existsByVendorIdAndTitleAndIdNot(vendor.getId(), title, contract.getId());
		if (taken) {
			throw ApiException.conflict("CONTRACT_TITLE_TAKEN", vendor.getName() + " already has a contract called " + title);
		}
		if (newVendor || !title.equalsIgnoreCase(contract.getTitle())) {
			recycleBin.requireNotInBin(VendorContract.class, Map.of("vendor_id", vendor.getId(), "title", title),
					"The contract " + title);
		}
		contract.setVendor(vendor);
		contract.setTitle(title);
		contract.setStartDate(request.startDate());
		contract.setEndDate(request.endDate());
		contract.setValue(money(request.value()));
		contract.setCurrency(VendorText.currency(request.currency()));
		contract.setRenewalDate(request.renewalDate());
		contract.setAutoRenew(Boolean.TRUE.equals(request.autoRenew()));
		contract.setNoticePeriodDays(request.noticePeriodDays());
		contract.setNotes(VendorText.trimToNull(request.notes()));
	}

	/** The fields an update audits; {@code value} is hidden in history from users without FINANCE_VIEW. */
	private static Map<String, Object> snapshot(VendorContract c) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("vendorId", c.getVendor().getId());
		values.put("title", c.getTitle());
		values.put("startDate", c.getStartDate());
		values.put("endDate", c.getEndDate());
		values.put("value", c.getValue());
		values.put("currency", c.getCurrency());
		values.put("renewalDate", c.getRenewalDate());
		values.put("autoRenew", c.isAutoRenew());
		values.put("noticePeriodDays", c.getNoticePeriodDays());
		values.put("notes", c.getNotes());
		return values;
	}

	static ContractDto toDto(VendorContract c, LocalDate today) {
		LocalDate renewsOn = ContractRules.renewsOn(c);
		return new ContractDto(c.getId(), VendorRef.of(c.getVendor()), c.getTitle(), c.getStartDate(), c.getEndDate(),
				c.getValue(), c.getCurrency(), c.getRenewalDate(), c.isAutoRenew(), c.getNoticePeriodDays(), c.getNotes(),
				ContractRules.status(c, today), renewsOn, ContractRules.daysUntilRenewal(c, today),
				ContractRules.noticeBy(renewsOn, c.getNoticePeriodDays()),
				c.getDocuments().stream().map(DocumentDto::of).toList(), c.getVersion(), c.getCreatedAt(),
				c.getUpdatedAt());
	}

	private VendorContract load(Long id) {
		return repository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("CONTRACT_NOT_FOUND", "Contract not found"));
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

	private static Expression<LocalDate> renewsOn(Root<VendorContract> root, CriteriaBuilder cb) {
		return cb.coalesce(root.<LocalDate>get("renewalDate"), root.<LocalDate>get("endDate"));
	}

	static Specification<VendorContract> spec(ContractFilter f, LocalDate today) {
		Specification<VendorContract> spec = (root, query, cb) -> cb.conjunction();
		if (VendorText.trimToNull(f.search()) != null) {
			String pattern = VendorText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
					cb.like(cb.lower(root.get("vendor").get("name")), pattern, '\\')));
		}
		if (f.vendorId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("vendor").get("id"), f.vendorId()));
		}
		if (f.status() != null) {
			spec = spec.and((root, query, cb) -> switch (f.status()) {
				case UPCOMING -> cb.greaterThan(root.get("startDate"), today);
				case ACTIVE -> cb.and(cb.lessThanOrEqualTo(root.get("startDate"), today),
						cb.or(cb.isNull(root.get("endDate")), cb.greaterThanOrEqualTo(root.get("endDate"), today)));
				case EXPIRED -> cb.lessThan(root.get("endDate"), today);
			});
		}
		if (f.renewalWithinDays() != null) {
			LocalDate until = today.plusDays(Math.max(f.renewalWithinDays(), 0));
			spec = spec.and((root, query, cb) -> cb.and(cb.between(renewsOn(root, cb), today, until),
					cb.or(cb.isNull(root.get("endDate")), cb.greaterThanOrEqualTo(root.get("endDate"), today))));
		}
		if (f.autoRenew() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("autoRenew"), f.autoRenew()));
		}
		return spec;
	}

	/** Amounts with two decimals (validated to have at most two). */
	static BigDecimal money(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY);
	}

}
