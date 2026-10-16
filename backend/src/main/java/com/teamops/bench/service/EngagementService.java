package com.teamops.bench.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.bench.dto.BenchDtos.ClientRef;
import com.teamops.bench.dto.BenchDtos.EngagementDto;
import com.teamops.bench.dto.BenchDtos.SaveEngagement;
import com.teamops.bench.entity.Client;
import com.teamops.bench.entity.ClientEngagement;
import com.teamops.bench.repository.ClientEngagementRepository;
import com.teamops.bench.repository.ClientRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Client engagements (Phase X6): client, name (unique per client), location, start and end dates and account manager.
 * Status follows from the dates on today's business date. Changes are audited.
 */
@Service
@RequiredArgsConstructor
public class EngagementService {

	public static final String ENTITY = "CLIENT_ENGAGEMENT";

	private final ClientEngagementRepository repository;

	private final ClientRepository clients;

	private final UserRepository users;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	private final RecycleBinService recycleBin;

	public record EngagementFilter(String search, Long clientId, EngagementStatus status, Long accountManagerId) {
	}

	@Transactional(readOnly = true)
	public PageResponse<EngagementDto> search(EngagementFilter filter, Pageable pageable) {
		LocalDate today = calendar.today();
		return PageResponse.of(repository.findAll(spec(filter, today), pageable).map(e -> toDto(e, today)));
	}

	@Transactional(readOnly = true)
	public EngagementDto get(Long id) {
		return toDto(load(id), calendar.today());
	}

	@Transactional
	public EngagementDto create(SaveEngagement request, AuthenticatedUser actor, ClientInfo info) {
		ClientEngagement engagement = new ClientEngagement();
		apply(engagement, request);
		ClientEngagement saved = repository.saveAndFlush(engagement);
		auditService.record(AuditAction.ENGAGEMENT_CREATED, actor.id(), ENTITY, saved.getId(), identity(saved), info);
		return toDto(saved, calendar.today());
	}

	@Transactional
	public EngagementDto update(Long id, SaveEngagement request, AuthenticatedUser actor, ClientInfo info) {
		ClientEngagement engagement = load(id);
		if (request.version() == null || !Objects.equals(engagement.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE",
					"Someone else changed this engagement just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(engagement);
		apply(engagement, request);
		AuditChanges changes = new AuditChanges();
		snapshot(engagement).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.ENGAGEMENT_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), info);
		}
		return toDto(engagement, calendar.today());
	}

	/** Moves the engagement to the recycle bin. Audited. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo info) {
		ClientEngagement engagement = load(id);
		recycleBin.moveToBin(engagement, actor);
		auditService.record(AuditAction.ENGAGEMENT_DELETED, actor.id(), ENTITY, id, identity(engagement), info);
	}

	private void apply(ClientEngagement engagement, SaveEngagement request) {
		Client client = clients.findById(request.clientId())
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_CLIENT", "Client not found"));
		String name = request.name().trim();
		boolean sameKey = engagement.getId() != null && engagement.getClient().getId().equals(client.getId())
				&& engagement.getName().equalsIgnoreCase(name);
		if (!sameKey) {
			boolean taken = engagement.getId() == null ? repository.existsByClientIdAndName(client.getId(), name)
					: repository.existsByClientIdAndNameAndIdNot(client.getId(), name, engagement.getId());
			if (taken) {
				throw ApiException.conflict("ENGAGEMENT_NAME_TAKEN",
						client.getName() + " already has an engagement called " + name);
			}
			recycleBin.requireNotInBin(ClientEngagement.class, Map.of("client_id", client.getId(), "name", name),
					"The engagement name " + name);
		}
		if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
			throw ApiException.badRequest("INVALID_DATES", "The end date must be on or after the start date");
		}
		User manager = null;
		if (request.accountManagerId() != null) {
			manager = users.findById(request.accountManagerId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "Account manager not found"));
			boolean unchanged = engagement.getAccountManager() != null
					&& engagement.getAccountManager().getId().equals(manager.getId());
			if (!unchanged && !manager.isActive()) {
				throw ApiException.badRequest("USER_INACTIVE", manager.getFullName() + " is not an active user");
			}
		}
		engagement.setClient(client);
		engagement.setName(name);
		engagement.setLocation(BenchText.trimToNull(request.location()));
		engagement.setStartDate(request.startDate());
		engagement.setEndDate(request.endDate());
		engagement.setAccountManager(manager);
		engagement.setNotes(BenchText.trimToNull(request.notes()));
	}

	private static Map<String, Object> snapshot(ClientEngagement e) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("clientId", e.getClient().getId());
		values.put("name", e.getName());
		values.put("location", e.getLocation());
		values.put("startDate", e.getStartDate());
		values.put("endDate", e.getEndDate());
		values.put("accountManagerId", e.getAccountManager() == null ? null : e.getAccountManager().getId());
		values.put("notes", e.getNotes());
		return values;
	}

	private static Map<String, Object> identity(ClientEngagement engagement) {
		Map<String, Object> details = new HashMap<>();
		details.put("name", engagement.getName());
		details.put("clientId", engagement.getClient().getId());
		details.put("client", engagement.getClient().getName());
		return details;
	}

	private static EngagementDto toDto(ClientEngagement e, LocalDate today) {
		return new EngagementDto(e.getId(), ClientRef.of(e.getClient()), e.getName(), e.getLocation(), e.getStartDate(),
				e.getEndDate(), EngagementStatus.of(e.getStartDate(), e.getEndDate(), today),
				UserSummary.of(e.getAccountManager()), e.getNotes(), e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private ClientEngagement load(Long id) {
		return repository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("ENGAGEMENT_NOT_FOUND", "Engagement not found"));
	}

	/** Status filters match {@link EngagementStatus#of} on the same day. */
	private static Specification<ClientEngagement> spec(EngagementFilter f, LocalDate today) {
		Specification<ClientEngagement> spec = (root, query, cb) -> cb.conjunction();
		if (BenchText.trimToNull(f.search()) != null) {
			String pattern = BenchText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
					cb.like(cb.lower(root.get("client").get("name")), pattern, '\\'),
					cb.like(cb.lower(root.get("location")), pattern, '\\')));
		}
		if (f.clientId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("client").get("id"), f.clientId()));
		}
		if (f.accountManagerId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("accountManager").get("id"), f.accountManagerId()));
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
