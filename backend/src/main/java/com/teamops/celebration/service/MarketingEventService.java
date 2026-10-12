package com.teamops.celebration.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.celebration.dto.CelebrationDtos.Attachment;
import com.teamops.celebration.dto.CelebrationDtos.LinkedCampaign;
import com.teamops.celebration.dto.CelebrationDtos.MarketingEventDto;
import com.teamops.celebration.dto.CelebrationDtos.SaveMarketingEvent;
import com.teamops.celebration.entity.LinkedInPost;
import com.teamops.celebration.entity.MarketingEvent;
import com.teamops.celebration.entity.MarketingEventStatus;
import com.teamops.celebration.entity.MarketingEventType;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.repository.MarketingEventRepository;
import com.teamops.celebration.service.OccasionAttachmentService.Download;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Upcoming events, webinars, event marketing and email marketing. Everyone signed in sees them; CELEBRATIONS_EDIT
 * holders change them. A linked email campaign's details are only shown to CAMPAIGN_VIEW holders. Audited.
 */
@Service
@RequiredArgsConstructor
public class MarketingEventService {

	public static final String ENTITY = "MARKETING_EVENT";

	static final String CAMPAIGN_VIEW = "CAMPAIGN_VIEW";

	private final MarketingEventRepository repository;

	private final EmailCampaignRepository campaignRepository;

	private final UserRepository userRepository;

	private final OccasionAttachmentService attachments;

	private final LinkedInPostService linkedInPosts;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	/** {@code from}/{@code to}: business-zone start dates, inclusive. */
	public record EventFilter(String search, Set<MarketingEventType> types, Set<MarketingEventStatus> statuses,
			LocalDate from, LocalDate to, Long ownerId) {
	}

	@Transactional(readOnly = true)
	public PageResponse<MarketingEventDto> search(EventFilter filter, Pageable pageable, AuthenticatedUser viewer) {
		Page<MarketingEvent> page = repository.findAll(spec(filter), pageable);
		Map<Long, MarketingEventDto> dtos = toDtos(page.getContent(), viewer);
		return PageResponse.of(page.map(event -> dtos.get(event.getId())));
	}

	@Transactional(readOnly = true)
	public MarketingEventDto get(Long id, AuthenticatedUser viewer) {
		return toDtos(List.of(load(id)), viewer).get(id);
	}

	@Transactional
	public MarketingEventDto create(SaveMarketingEvent request, AuthenticatedUser actor, ClientInfo client) {
		MarketingEvent event = new MarketingEvent();
		event.setCreatedBy(userRepository.getReferenceById(actor.id()));
		apply(event, request);
		MarketingEvent saved = repository.saveAndFlush(event);
		Map<String, Object> details = new HashMap<>();
		details.put("title", saved.getTitle());
		details.put("eventType", saved.getEventType());
		details.put("startAt", saved.getStartAt());
		auditService.record(AuditAction.MARKETING_EVENT_CREATED, actor.id(), ENTITY, saved.getId(), details, client);
		return get(saved.getId(), actor);
	}

	@Transactional
	public MarketingEventDto update(Long id, SaveMarketingEvent request, AuthenticatedUser actor, ClientInfo client) {
		MarketingEvent event = load(id);
		if (request.version() == null || !Objects.equals(event.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this event just now. Reload and try again.");
		}
		LocalDate startBefore = startDate(event);
		AuditChanges changes = new AuditChanges().track("title", event.getTitle(), request.title().trim())
			.track("eventType", event.getEventType(), request.eventType())
			.track("status", event.getStatus(), request.status() == null ? event.getStatus() : request.status())
			.track("startAt", event.getStartAt(), request.startAt())
			.track("endAt", event.getEndAt(), request.endAt())
			.track("location", event.getLocation(), CelebrationText.trimToNull(request.location()))
			.track("onlineLink", event.getOnlineLink(), CelebrationText.trimToNull(request.onlineLink()))
			.track("registrationLink", event.getRegistrationLink(), CelebrationText.trimToNull(request.registrationLink()))
			.track("description", event.getDescription(), CelebrationText.trimToNull(request.description()))
			.track("ownerId", event.getOwner().getId(), request.ownerId())
			.track("emailCampaignId", event.getEmailCampaign() == null ? null : event.getEmailCampaign().getId(),
					request.emailCampaignId());
		apply(event, request);
		repository.flush();
		LocalDate start = startDate(event);
		if (!start.equals(startBefore)) {
			linkedInPosts.relocate(OccasionKind.EVENT, id, calendar.today(), date -> start);
		}
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.MARKETING_EVENT_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		return get(id, actor);
	}

	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		MarketingEvent event = load(id);
		attachments.deleteAll(OccasionKind.EVENT, id);
		linkedInPosts.deleteFor(Set.of(OccasionKind.EVENT), id);
		repository.delete(event);
		Map<String, Object> details = new HashMap<>();
		details.put("title", event.getTitle());
		details.put("eventType", event.getEventType());
		details.put("startAt", event.getStartAt());
		auditService.record(AuditAction.MARKETING_EVENT_DELETED, actor.id(), ENTITY, id, details, client);
	}

	// --- attachments ------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<Attachment> attachments(Long id) {
		load(id);
		return attachments.list(OccasionKind.EVENT, id);
	}

	@Transactional
	public List<Attachment> addAttachment(Long id, MultipartFile upload, AuthenticatedUser actor, ClientInfo client) {
		load(id);
		String name = attachments.add(OccasionKind.EVENT, id, upload, actor.id());
		auditService.record(AuditAction.MARKETING_EVENT_UPDATED, actor.id(), ENTITY, id, Map.of("attachmentAdded", name),
				client);
		return attachments.list(OccasionKind.EVENT, id);
	}

	@Transactional(readOnly = true)
	public Download download(Long id, Long fileId) {
		return attachments.download(OccasionKind.EVENT, id, fileId);
	}

	@Transactional
	public List<Attachment> deleteAttachment(Long id, Long fileId, AuthenticatedUser actor, ClientInfo client) {
		String name = attachments.delete(OccasionKind.EVENT, id, fileId);
		auditService.record(AuditAction.MARKETING_EVENT_UPDATED, actor.id(), ENTITY, id,
				Map.of("attachmentRemoved", name), client);
		return attachments.list(OccasionKind.EVENT, id);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void apply(MarketingEvent event, SaveMarketingEvent request) {
		if (request.endAt() != null && !request.endAt().isAfter(request.startAt())) {
			throw ApiException.badRequest("INVALID_DATES", "The end must be after the start");
		}
		User owner = Objects.equals(event.getOwner() == null ? null : event.getOwner().getId(), request.ownerId())
				? event.getOwner()
				: userRepository.findById(request.ownerId())
					.filter(User::isActive)
					.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "The owner must be an active user"));
		EmailCampaign campaign = null;
		if (request.emailCampaignId() != null) {
			campaign = campaignRepository.findById(request.emailCampaignId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_CAMPAIGN", "Email campaign not found"));
		}
		event.setTitle(request.title().trim());
		event.setEventType(request.eventType());
		if (request.status() != null) {
			event.setStatus(request.status());
		}
		event.setStartAt(request.startAt());
		event.setEndAt(request.endAt());
		event.setLocation(CelebrationText.trimToNull(request.location()));
		event.setOnlineLink(CelebrationText.link(request.onlineLink(), "Online link"));
		event.setRegistrationLink(CelebrationText.link(request.registrationLink(), "Registration link"));
		event.setDescription(CelebrationText.trimToNull(request.description()));
		event.setOwner(owner);
		event.setEmailCampaign(campaign);
	}

	private LocalDate startDate(MarketingEvent event) {
		return LocalDate.ofInstant(event.getStartAt(), calendar.zone());
	}

	private Map<Long, MarketingEventDto> toDtos(List<MarketingEvent> events, AuthenticatedUser viewer) {
		List<Long> ids = events.stream().map(MarketingEvent::getId).toList();
		Map<Long, Long> counts = attachments.counts(OccasionKind.EVENT, ids);
		Map<String, LinkedInPost> posts = linkedInPosts.forSubjects(OccasionKind.EVENT, ids);
		boolean canEdit = CelebrationAccess.canEdit(viewer);
		boolean campaigns = viewer.isSuperAdmin() || viewer.hasPermission(CAMPAIGN_VIEW);
		Map<Long, MarketingEventDto> dtos = new HashMap<>();
		for (MarketingEvent e : events) {
			LocalDate start = startDate(e);
			EmailCampaign campaign = e.getEmailCampaign();
			dtos.put(e.getId(), new MarketingEventDto(e.getId(), e.getTitle(), e.getEventType(),
					e.getEventType().category(), e.getStatus(), e.getStartAt(), e.getEndAt(), start, e.getLocation(),
					e.getOnlineLink(), e.getRegistrationLink(), e.getDescription(), UserSummary.of(e.getOwner()),
					campaign == null ? null : campaign.getId(),
					campaign == null || !campaigns ? null
							: new LinkedCampaign(campaign.getId(), campaign.getName(), campaign.getStatus(),
									campaign.getCampaignDate()),
					counts.getOrDefault(e.getId(), 0L),
					LinkedInPostService.dto(OccasionKind.EVENT, posts.get(Occasion.key(OccasionKind.EVENT, e.getId(), start))),
					e.getVersion(), canEdit));
		}
		return dtos;
	}

	private MarketingEvent load(Long id) {
		return repository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "Event not found"));
	}

	private Specification<MarketingEvent> spec(EventFilter f) {
		Specification<MarketingEvent> spec = (root, query, cb) -> cb.conjunction();
		if (f.search() != null && !f.search().isBlank()) {
			String pattern = CelebrationText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
					cb.like(cb.lower(root.get("location")), pattern, '\\')));
		}
		if (f.types() != null && !f.types().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("eventType").in(f.types()));
		}
		if (f.statuses() != null && !f.statuses().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("status").in(f.statuses()));
		}
		if (f.from() != null) {
			var from = calendar.startOf(f.from());
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startAt"), from));
		}
		if (f.to() != null) {
			var to = calendar.startOf(f.to().plusDays(1));
			spec = spec.and((root, query, cb) -> cb.lessThan(root.get("startAt"), to));
		}
		if (f.ownerId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
		}
		return spec;
	}

}
