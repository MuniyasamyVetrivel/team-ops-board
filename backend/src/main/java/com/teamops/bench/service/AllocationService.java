package com.teamops.bench.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.bench.dto.BenchDtos.AllocationCheck;
import com.teamops.bench.dto.BenchDtos.AllocationDto;
import com.teamops.bench.dto.BenchDtos.AllocationSaved;
import com.teamops.bench.dto.BenchDtos.ConsultantRef;
import com.teamops.bench.dto.BenchDtos.EngagementRef;
import com.teamops.bench.dto.BenchDtos.OverAllocation;
import com.teamops.bench.dto.BenchDtos.SaveAllocation;
import com.teamops.bench.entity.Allocation;
import com.teamops.bench.entity.ClientEngagement;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.AllocationRepository;
import com.teamops.bench.repository.ClientEngagementRepository;
import com.teamops.bench.repository.ConsultantRepository;
import com.teamops.bench.service.BenchMath.Period;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.service.RecycleBinService;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;

/**
 * Allocations (Phase X6): a consultant on a client engagement, with a role, dates inside the engagement's, a
 * percentage (1-100) and billable or not. A consultant's total may go above 100% for a period: the save goes through
 * and answers which stretches are over-allocated, so the person can fix it. Changes are audited; a delete moves the
 * allocation to the recycle bin.
 */
@Service
@RequiredArgsConstructor
public class AllocationService {

	public static final String ENTITY = "ALLOCATION";

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final AllocationRepository repository;

	private final ConsultantRepository consultants;

	private final ClientEngagementRepository engagements;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	private final RecycleBinService recycleBin;

	/** @param search role, consultant name or employee ID, engagement or client name */
	public record AllocationFilter(String search, Long consultantId, Long engagementId, Long clientId,
			PeriodStatus status, Boolean billable) {
	}

	@Transactional(readOnly = true)
	public PageResponse<AllocationDto> search(AllocationFilter filter, Pageable pageable) {
		LocalDate today = calendar.today();
		return PageResponse.of(repository.findAll(spec(filter, today), pageable).map(a -> toDto(a, today)));
	}

	@Transactional(readOnly = true)
	public AllocationDto get(Long id) {
		return toDto(load(id), calendar.today());
	}

	/**
	 * Where an allocation with these dates and percentage would take the consultant above 100% (for a form, before
	 * saving). {@code excludeId} is the allocation being edited.
	 */
	@Transactional(readOnly = true)
	public AllocationCheck check(Long consultantId, LocalDate startDate, LocalDate endDate, int percent,
			Long excludeId) {
		if (endDate != null && endDate.isBefore(startDate)) {
			throw invalidDates();
		}
		if (percent < 1 || percent > BenchMath.FULL) {
			throw ApiException.badRequest("INVALID_PERCENT", "The allocation must be between 1% and 100%");
		}
		List<Period> periods = new ArrayList<>(periodsOf(consultantId, excludeId));
		periods.add(new Period(startDate, endDate, percent, true));
		return new AllocationCheck(overAllocations(periods, startDate, endDate));
	}

	@Transactional
	public AllocationSaved create(SaveAllocation request, AuthenticatedUser actor, ClientInfo client) {
		Allocation allocation = new Allocation();
		apply(allocation, request);
		Allocation saved = repository.saveAndFlush(allocation);
		List<OverAllocation> over = overAllocationsOf(saved);
		Map<String, Object> details = identity(saved);
		if (!over.isEmpty()) {
			details.put("overAllocated", true);
		}
		auditService.record(AuditAction.ALLOCATION_CREATED, actor.id(), ENTITY, saved.getId(), details, client);
		return new AllocationSaved(toDto(saved, calendar.today()), over);
	}

	@Transactional
	public AllocationSaved update(Long id, SaveAllocation request, AuthenticatedUser actor, ClientInfo client) {
		Allocation allocation = load(id);
		if (request.version() == null || !Objects.equals(allocation.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE",
					"Someone else changed this allocation just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(allocation);
		apply(allocation, request);
		AuditChanges changes = new AuditChanges();
		snapshot(allocation).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.ALLOCATION_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		return new AllocationSaved(toDto(allocation, calendar.today()), overAllocationsOf(allocation));
	}

	/** Moves the allocation to the recycle bin. Audited. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		Allocation allocation = load(id);
		recycleBin.moveToBin(allocation, actor);
		auditService.record(AuditAction.ALLOCATION_DELETED, actor.id(), ENTITY, id, identity(allocation), client);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void apply(Allocation allocation, SaveAllocation request) {
		Consultant consultant = consultants.findDetailedById(request.consultantId())
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_CONSULTANT", "Consultant not found"));
		boolean newConsultant = allocation.getConsultant() == null
				|| !allocation.getConsultant().getId().equals(consultant.getId());
		if (newConsultant && consultant.getEmploymentStatus() == EmploymentStatus.LEFT) {
			throw ApiException.badRequest("CONSULTANT_LEFT", "This consultant has left and cannot be allocated");
		}
		ClientEngagement engagement = engagements.findDetailedById(request.engagementId())
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_ENGAGEMENT", "Engagement not found"));
		LocalDate start = request.startDate();
		LocalDate end = request.endDate();
		if (end != null && end.isBefore(start)) {
			throw invalidDates();
		}
		boolean beforeEngagement = start.isBefore(engagement.getStartDate());
		boolean afterEngagement = engagement.getEndDate() != null
				&& (end == null || end.isAfter(engagement.getEndDate()));
		if (beforeEngagement || afterEngagement) {
			throw ApiException.badRequest("OUTSIDE_ENGAGEMENT", "The allocation must fall within the engagement ("
					+ DATE.format(engagement.getStartDate()) + " to "
					+ (engagement.getEndDate() == null ? "open-ended" : DATE.format(engagement.getEndDate())) + ")");
		}
		allocation.setConsultant(consultant);
		allocation.setEngagement(engagement);
		allocation.setRole(request.role().trim());
		allocation.setStartDate(start);
		allocation.setEndDate(end);
		allocation.setAllocationPercent(request.allocationPercent());
		allocation.setBillable(request.billable() == null || request.billable());
		allocation.setNotes(BenchText.trimToNull(request.notes()));
	}

	/** Over-allocated stretches the saved allocation is part of (it is already flushed, so it counts). */
	private List<OverAllocation> overAllocationsOf(Allocation allocation) {
		return overAllocations(periodsOf(allocation.getConsultant().getId(), null), allocation.getStartDate(),
				allocation.getEndDate());
	}

	private List<Period> periodsOf(Long consultantId, Long excludeId) {
		return repository.findSlices(List.of(consultantId))
			.stream()
			.filter(slice -> !slice.getId().equals(excludeId))
			.map(AllocationService::period)
			.toList();
	}

	static Period period(AllocationRepository.Slice slice) {
		return new Period(slice.getStartDate(), slice.getEndDate(), slice.getAllocationPercent(), slice.isBillable());
	}

	private static List<OverAllocation> overAllocations(List<Period> periods, LocalDate from, LocalDate to) {
		return BenchMath.overAllocated(periods, from, to)
			.stream()
			.map(s -> new OverAllocation(s.from(), s.to(), s.totalPercent()))
			.toList();
	}

	private static Map<String, Object> snapshot(Allocation a) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("consultantId", a.getConsultant().getId());
		values.put("engagementId", a.getEngagement().getId());
		values.put("role", a.getRole());
		values.put("startDate", a.getStartDate());
		values.put("endDate", a.getEndDate());
		values.put("allocationPercent", a.getAllocationPercent());
		values.put("billable", a.isBillable());
		values.put("notes", a.getNotes());
		return values;
	}

	private static Map<String, Object> identity(Allocation a) {
		Map<String, Object> details = new HashMap<>();
		details.put("consultantId", a.getConsultant().getId());
		ConsultantRef consultant = ConsultantRef.of(a.getConsultant());
		if (consultant.fullName() != null) {
			details.put("consultant", consultant.fullName());
		}
		details.put("engagementId", a.getEngagement().getId());
		details.put("engagement", a.getEngagement().getName());
		details.put("client", a.getEngagement().getClient().getName());
		details.put("allocationPercent", a.getAllocationPercent());
		return details;
	}

	static AllocationDto toDto(Allocation a, LocalDate today) {
		PeriodStatus status = PeriodStatus.of(a.getStartDate(), a.getEndDate(), today);
		Long daysUntilEnd = a.getEndDate() == null || status == PeriodStatus.ENDED ? null
				: ChronoUnit.DAYS.between(today, a.getEndDate());
		return new AllocationDto(a.getId(), ConsultantRef.of(a.getConsultant()), EngagementRef.of(a.getEngagement()),
				a.getRole(), a.getStartDate(), a.getEndDate(), a.getAllocationPercent(), a.isBillable(), status,
				daysUntilEnd, a.getNotes(), a.getVersion(), a.getCreatedAt(), a.getUpdatedAt());
	}

	private Allocation load(Long id) {
		return repository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("ALLOCATION_NOT_FOUND", "Allocation not found"));
	}

	private static ApiException invalidDates() {
		return ApiException.badRequest("INVALID_DATES", "The end date must be on or after the start date");
	}

	/** Status filters match {@link PeriodStatus#of} on the same day. */
	private static Specification<Allocation> spec(AllocationFilter f, LocalDate today) {
		Specification<Allocation> spec = (root, query, cb) -> cb.conjunction();
		if (BenchText.trimToNull(f.search()) != null) {
			String pattern = BenchText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> {
				var engagement = root.join("engagement");
				var employee = root.join("consultant").join("employee", JoinType.LEFT);
				return cb.or(cb.like(cb.lower(root.get("role")), pattern, '\\'),
						cb.like(cb.lower(engagement.get("name")), pattern, '\\'),
						cb.like(cb.lower(engagement.get("client").get("name")), pattern, '\\'),
						cb.like(cb.lower(cb.concat(cb.concat(employee.get("firstName"), " "), employee.get("lastName"))),
								pattern, '\\'),
						cb.like(cb.lower(employee.get("employeeCode")), pattern, '\\'));
			});
		}
		if (f.consultantId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("consultant").get("id"), f.consultantId()));
		}
		if (f.engagementId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("engagement").get("id"), f.engagementId()));
		}
		if (f.clientId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("engagement").get("client").get("id"), f.clientId()));
		}
		if (f.billable() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("billable"), f.billable()));
		}
		if (f.status() != null) {
			spec = spec.and((root, query, cb) -> switch (f.status()) {
				case UPCOMING -> cb.greaterThan(root.get("startDate"), today);
				case ENDED -> cb.lessThan(root.get("endDate"), today);
				case ACTIVE -> cb.and(cb.lessThanOrEqualTo(root.get("startDate"), today),
						cb.or(cb.isNull(root.get("endDate")), cb.greaterThanOrEqualTo(root.get("endDate"), today)));
			});
		}
		return spec;
	}

}
