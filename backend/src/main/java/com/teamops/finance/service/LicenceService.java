package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.dto.FinanceDtos.AudRate;
import com.teamops.finance.dto.FinanceDtos.LicenceDto;
import com.teamops.finance.dto.FinanceDtos.SaveLicence;
import com.teamops.finance.entity.BillingCycle;
import com.teamops.finance.entity.ExchangeRate;
import com.teamops.finance.entity.FinanceLicence;
import com.teamops.finance.entity.LicenceAssignment;
import com.teamops.finance.entity.LicenceStatus;
import com.teamops.finance.repository.FinanceLicenceRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;
import com.teamops.vendor.dto.VendorDtos.VendorRef;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorRepository;
import com.teamops.vendor.service.VendorLinkSource.Link;
import com.teamops.vendor.service.VendorLinkSource.Links;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;

/**
 * Tools and licences (Phase X8). The name is unique, including licences in the recycle bin. A licence names a live
 * vendor (not a blacklisted one when the vendor changes), and its seats used are the users assigned to it; new owners
 * and assignees must be active users. Seats, utilisation, annual cost, its AUD equivalent and days to renewal are
 * computed on today's business date ({@link LicenceRules}, {@link ExchangeRateService}). Payment methods are labels:
 * never card numbers. Changes are audited; the cost is a finance amount.
 */
@Service
@RequiredArgsConstructor
public class LicenceService {

	public static final String ENTITY = "FINANCE_LICENCE";

	public static final int EXPORT_LIMIT = 10_000;

	/** The export's columns: the same as the import's, so an export can be edited and imported elsewhere. */
	public static final List<String> CSV_COLUMNS = List.of("name", "vendor", "owner_email", "department",
			"seats_paid", "billing_cycle", "cost", "currency", "start_date", "renewal_date", "payment_method",
			"card_last4", "status", "assigned_users", "notes");

	/** Assigned user emails in one CSV cell. */
	static final String LIST_SEPARATOR = ";";

	private final FinanceLicenceRepository repository;

	/** Not VendorService: it lists the vendor link sources, one of which reads licences through this service. */
	private final VendorRepository vendors;

	private final UserRepository users;

	private final DepartmentRepository departments;

	private final ExchangeRateService rates;

	private final AuditService auditService;

	private final RecycleBinService recycleBin;

	private final BusinessCalendar calendar;

	private final AppSettingsService settings;

	/** @param renewalWithinDays only licences, not cancelled, renewing between today and this many days from now */
	public record LicenceFilter(String search, Long vendorId, Long departmentId, LicenceStatus status,
			BillingCycle billingCycle, Integer renewalWithinDays) {
	}

	@Transactional(readOnly = true)
	public PageResponse<LicenceDto> search(LicenceFilter filter, Pageable pageable) {
		LocalDate today = calendar.today();
		Page<FinanceLicence> page = repository.findAll(spec(filter, today), pageable);
		Map<String, ExchangeRate> audRates = rates.ratesOn(page.map(FinanceLicence::getCurrency).toSet(), today);
		return PageResponse.of(page.map(licence -> toDto(licence, audRates, today)));
	}

	@Transactional(readOnly = true)
	public LicenceDto get(Long id) {
		FinanceLicence licence = load(id);
		LocalDate today = calendar.today();
		return toDto(licence, rates.ratesOn(Set.of(licence.getCurrency()), today), today);
	}

	/** Every active and trial licence with its computed figures on today's date (the dashboard's licence figures). */
	@Transactional(readOnly = true)
	public List<LicenceDto> inUse() {
		LocalDate today = calendar.today();
		List<FinanceLicence> licences = repository
			.findAll((root, query, cb) -> cb.notEqual(root.get("status"), LicenceStatus.CANCELLED), Sort.by("name", "id"));
		Map<String, ExchangeRate> audRates = rates.ratesOn(
				licences.stream().map(FinanceLicence::getCurrency).collect(Collectors.toSet()), today);
		return licences.stream().map(licence -> toDto(licence, audRates, today)).toList();
	}

	/** The vendor page's licences (newest first), as links; the caller checks the viewer may see finance data. */
	@Transactional(readOnly = true)
	public Links linksForVendor(Long vendorId, int limit) {
		Page<FinanceLicence> page = repository.findByVendorId(vendorId,
				PageRequest.of(0, Math.max(limit, 1), Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
		LocalDate today = calendar.today();
		List<Link> items = page.getContent().stream().map(l -> new Link(l.getId(), l.getName(), detail(l, today),
				l.getStatus().name())).toList();
		return new Links(page.getTotalElements(), items);
	}

	/** Live licences that name the vendor. */
	@Transactional(readOnly = true)
	public long countUsingVendor(Long vendorId) {
		return repository.countByVendorId(vendorId);
	}

	@Transactional
	public LicenceDto create(SaveLicence request, AuthenticatedUser actor, ClientInfo info) {
		FinanceLicence licence = new FinanceLicence();
		apply(licence, request);
		FinanceLicence saved = repository.saveAndFlush(licence);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", saved.getName());
		details.put("vendorId", saved.getVendorId());
		details.put("billingCycle", saved.getBillingCycle());
		details.put("cost", saved.getCost());
		details.put("currency", saved.getCurrency());
		auditService.record(AuditAction.LICENCE_CREATED, actor.id(), ENTITY, saved.getId(), details, info);
		return get(saved.getId());
	}

	@Transactional
	public LicenceDto update(Long id, SaveLicence request, AuthenticatedUser actor, ClientInfo info) {
		FinanceLicence licence = load(id);
		if (request.version() == null || !Objects.equals(licence.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this licence just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(licence);
		apply(licence, request);
		AuditChanges changes = new AuditChanges();
		snapshot(licence).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.LICENCE_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), info);
		}
		return get(id);
	}

	/** Moves the licence to the recycle bin; its assigned users stay with it. Audited. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo info) {
		FinanceLicence licence = load(id);
		recycleBin.moveToBin(licence, actor);
		auditService.record(AuditAction.LICENCE_DELETED, actor.id(), ENTITY, id, Map.of("name", licence.getName()), info);
	}

	/** CSV in the import's columns. */
	@Transactional(readOnly = true)
	public byte[] export(LicenceFilter filter) {
		List<List<String>> rows = repository.findAll(spec(filter, calendar.today()), Sort.by("name", "id"))
			.stream()
			.limit(EXPORT_LIMIT)
			.map(l -> List.of(l.getName(), l.getVendor() == null ? "" : l.getVendor().getName(),
					l.getOwner() == null ? "" : l.getOwner().getEmail(),
					l.getDepartment() == null ? "" : l.getDepartment().getCode(), text(l.getSeatsPaid()),
					l.getBillingCycle().name(), l.getCost() == null ? "" : l.getCost().toPlainString(), l.getCurrency(),
					text(l.getStartDate()), text(l.getRenewalDate()), text(l.getPaymentMethod()), text(l.getCardLast4()),
					l.getStatus().name(),
					l.getAssignments().stream().map(a -> a.getUser().getEmail()).collect(Collectors.joining(LIST_SEPARATOR + " ")),
					text(l.getNotes())))
			.toList();
		return CsvWriter.write(CSV_COLUMNS, rows);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void apply(FinanceLicence licence, SaveLicence request) {
		String name = request.name().trim();
		boolean taken = licence.getId() == null ? repository.existsByName(name)
				: repository.existsByNameAndIdNot(name, licence.getId());
		if (taken) {
			throw ApiException.conflict("LICENCE_NAME_TAKEN", "Another tool or licence is already called " + name);
		}
		if (!name.equalsIgnoreCase(licence.getName() == null ? "" : licence.getName())) {
			recycleBin.requireNotInBin(FinanceLicence.class, Map.of("name", name), "The licence name " + name);
		}
		Vendor vendor = null;
		if (request.vendorId() != null) {
			boolean unchanged = request.vendorId().equals(licence.getVendorId()) && licence.getVendor() != null;
			vendor = unchanged ? licence.getVendor() : vendors.findById(request.vendorId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_VENDOR", "Vendor not found"));
			if (!unchanged && vendor.getStatus() == VendorStatus.BLACKLISTED) {
				throw ApiException.badRequest("VENDOR_BLACKLISTED", vendor.getName() + " is blacklisted");
			}
		}
		LicenceRules.checkDates(request.startDate(), request.renewalDate());
		String paymentMethod = FinanceText.trimToNull(request.paymentMethod());
		String last4 = FinanceText.trimToNull(request.cardLast4());
		LicenceRules.checkPaymentMethod(paymentMethod, last4);

		licence.setName(name);
		licence.setVendorId(request.vendorId());
		// The read-only view as well, so the saved licence reads back with its vendor in this transaction.
		licence.setVendor(vendor);
		licence.setOwner(person(request.ownerId(), licence.getOwner()));
		licence.setDepartment(request.departmentId() == null ? null : departments.findById(request.departmentId())
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found")));
		licence.setSeatsPaid(request.seatsPaid());
		licence.setBillingCycle(request.billingCycle());
		licence.setCost(request.cost() == null ? null : request.cost().setScale(2, RoundingMode.UNNECESSARY));
		licence.setCurrency(FinanceText.currency(request.currency(), settings.financeDefaultCurrency()));
		licence.setStartDate(request.startDate());
		licence.setRenewalDate(request.renewalDate());
		licence.setPaymentMethod(paymentMethod);
		licence.setCardLast4(last4);
		licence.setStatus(request.status() == null ? LicenceStatus.ACTIVE : request.status());
		licence.setNotes(FinanceText.trimToNull(request.notes()));
		replaceAssignments(licence, request.assignedUserIds() == null ? List.of() : request.assignedUserIds());
	}

	/** An owner: unchanged, or an active user (400 UNKNOWN_USER, USER_INACTIVE). */
	private User person(Long userId, User current) {
		if (userId == null) {
			return null;
		}
		if (current != null && current.getId().equals(userId)) {
			return current;
		}
		User user = users.findById(userId).orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "User not found"));
		if (!user.isActive()) {
			throw ApiException.badRequest("USER_INACTIVE", user.getFullName() + " is not an active user");
		}
		return user;
	}

	/** Keeps the users still assigned, removes the rest and adds new ones, who must be active users. */
	private void replaceAssignments(FinanceLicence licence, List<Long> requested) {
		Set<Long> wanted = new LinkedHashSet<>(requested);
		Map<Long, LicenceAssignment> existing = new HashMap<>();
		licence.getAssignments().forEach(a -> existing.put(a.getUser().getId(), a));
		Set<Long> added = wanted.stream().filter(id -> !existing.containsKey(id)).collect(Collectors.toCollection(LinkedHashSet::new));
		Map<Long, User> found = users.findAllById(added).stream().collect(Collectors.toMap(User::getId, u -> u));
		for (Long id : added) {
			User user = found.get(id);
			if (user == null) {
				throw ApiException.badRequest("UNKNOWN_USER", "User " + id + " not found");
			}
			if (!user.isActive()) {
				throw ApiException.badRequest("USER_INACTIVE", user.getFullName() + " is not an active user");
			}
		}
		licence.getAssignments().removeIf(a -> !wanted.contains(a.getUser().getId()));
		for (Long id : added) {
			LicenceAssignment assignment = new LicenceAssignment();
			assignment.setLicence(licence);
			assignment.setUser(found.get(id));
			licence.getAssignments().add(assignment);
		}
	}

	/** The fields an update audits; assigned users as names, the cost hidden from non-finance viewers. */
	private static Map<String, Object> snapshot(FinanceLicence l) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", l.getName());
		values.put("vendorId", l.getVendorId());
		values.put("ownerId", l.getOwner() == null ? null : l.getOwner().getId());
		values.put("departmentId", l.getDepartment() == null ? null : l.getDepartment().getId());
		values.put("seatsPaid", l.getSeatsPaid());
		values.put("billingCycle", l.getBillingCycle());
		values.put("cost", l.getCost());
		values.put("currency", l.getCurrency());
		values.put("startDate", l.getStartDate());
		values.put("renewalDate", l.getRenewalDate());
		values.put("paymentMethod", l.getPaymentMethod());
		values.put("cardLast4", l.getCardLast4());
		values.put("status", l.getStatus());
		values.put("notes", l.getNotes());
		values.put("assignedUsers", l.getAssignments()
			.stream()
			.map(a -> a.getUser().getFullName())
			.sorted()
			.toList());
		return values;
	}

	/** "18 of 20 seats · renews 2027-03-01" for the vendor page. */
	private static String detail(FinanceLicence l, LocalDate today) {
		List<String> parts = new ArrayList<>();
		int used = l.getAssignments().size();
		parts.add(l.getSeatsPaid() == null ? used + (used == 1 ? " user" : " users") : used + " of " + l.getSeatsPaid() + " seats");
		Long days = LicenceRules.daysUntilRenewal(l.getRenewalDate(), l.getStatus(), today);
		if (days != null) {
			parts.add((l.getStatus() == LicenceStatus.TRIAL ? "trial ends " : "renews ") + l.getRenewalDate());
		}
		return String.join(" · ", parts);
	}

	LicenceDto toDto(FinanceLicence l, Map<String, ExchangeRate> audRates, LocalDate today) {
		int used = l.getAssignments().size();
		BigDecimal annual = LicenceRules.annualCost(l.getCost(), l.getBillingCycle());
		AudRate audRate = audRate(l.getCurrency(), audRates);
		List<UserSummary> assigned = l.getAssignments()
			.stream()
			.map(LicenceAssignment::getUser)
			.sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
			.map(UserSummary::of)
			.toList();
		return new LicenceDto(l.getId(), l.getName(), l.getVendorId(), VendorRef.of(l.getVendor()),
				UserSummary.of(l.getOwner()), l.getDepartment() == null ? null : DepartmentSummary.of(l.getDepartment()),
				l.getSeatsPaid(), used, LicenceRules.seatsFree(l.getSeatsPaid(), used),
				LicenceRules.utilisationPercent(l.getSeatsPaid(), used),
				LicenceRules.underUsed(l.getSeatsPaid(), used, l.getStatus()),
				LicenceRules.overAssigned(l.getSeatsPaid(), used), assigned, l.getBillingCycle(), l.getCost(),
				l.getCurrency(), annual, audRate == null ? null : ExchangeRateService.toAud(annual, audRate.rate()), audRate,
				l.getStartDate(), l.getRenewalDate(), LicenceRules.daysUntilRenewal(l.getRenewalDate(), l.getStatus(), today),
				l.getPaymentMethod(), l.getCardLast4(), l.getStatus(), l.getNotes(), l.getVersion(), l.getCreatedAt(),
				l.getUpdatedAt());
	}

	private static AudRate audRate(String currency, Map<String, ExchangeRate> audRates) {
		if (FinanceModule.BASE_CURRENCY.equals(currency)) {
			return new AudRate(BigDecimal.ONE, null);
		}
		ExchangeRate rate = audRates.get(currency);
		return rate == null ? null : new AudRate(rate.getRateToAud(), rate.getEffectiveFrom());
	}

	private FinanceLicence load(Long id) {
		return repository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("LICENCE_NOT_FOUND", "Tool or licence not found"));
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

	static Specification<FinanceLicence> spec(LicenceFilter f, LocalDate today) {
		Specification<FinanceLicence> spec = (root, query, cb) -> cb.conjunction();
		if (FinanceText.trimToNull(f.search()) != null) {
			String pattern = FinanceText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> {
				var vendor = root.join("vendor", JoinType.LEFT);
				return cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
						cb.like(cb.lower(vendor.get("name")), pattern, '\\'),
						cb.like(cb.lower(root.get("paymentMethod")), pattern, '\\'));
			});
		}
		if (f.vendorId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("vendorId"), f.vendorId()));
		}
		if (f.departmentId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("department").get("id"), f.departmentId()));
		}
		if (f.status() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
		}
		if (f.billingCycle() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("billingCycle"), f.billingCycle()));
		}
		if (f.renewalWithinDays() != null) {
			LocalDate until = today.plusDays(Math.max(f.renewalWithinDays(), 0));
			spec = spec.and((root, query, cb) -> cb.and(cb.between(root.get("renewalDate"), today, until),
					cb.notEqual(root.get("status"), LicenceStatus.CANCELLED)));
		}
		return spec;
	}

}
