package com.teamops.announcement.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.announcement.dto.AnnouncementDtos.AnnouncementItem;
import com.teamops.announcement.dto.AnnouncementDtos.Save;
import com.teamops.announcement.dto.AnnouncementDtos.Stats;
import com.teamops.announcement.dto.AnnouncementDtos.UnreadCount;
import com.teamops.announcement.entity.Announcement;
import com.teamops.announcement.entity.AnnouncementState;
import com.teamops.announcement.repository.AnnouncementReadRepository;
import com.teamops.announcement.repository.AnnouncementReadRepository.Counts;
import com.teamops.announcement.repository.AnnouncementReadRepository.Receipt;
import com.teamops.announcement.repository.AnnouncementRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AccessScope;
import com.teamops.common.security.AccessScopeService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentCount;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.service.NotificationService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;
import com.teamops.user.repository.UserSpecifications;

import lombok.RequiredArgsConstructor;

/**
 * Announcements (brief section 15). People see active announcements for everyone and for their own or managed
 * departments; Super Admins see all. Managing (ANNOUNCEMENT_MANAGE) is limited to the actor's departments, and only
 * a Super Admin addresses everyone. Publishing notifies the audience; read and acknowledgement are tracked per
 * person.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

	static final String ANNOUNCEMENT_MANAGE = "ANNOUNCEMENT_MANAGE";

	private final AnnouncementRepository announcementRepository;

	private final AnnouncementReadRepository readRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final AccessScopeService accessScopeService;

	private final NotificationService notificationService;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	@Transactional(readOnly = true)
	public PageResponse<AnnouncementItem> list(AnnouncementState state, Pageable pageable, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		Instant now = calendar.now();
		Specification<Announcement> spec;
		if (state == AnnouncementState.ACTIVE) {
			spec = AnnouncementRepository.inState(state, now).and(AnnouncementRepository.audience(audienceOf(actor, scope)));
		}
		else {
			if (!actor.hasPermission(ANNOUNCEMENT_MANAGE)) {
				throw ApiException.forbidden("FORBIDDEN", "Only announcement managers see scheduled or expired announcements");
			}
			spec = AnnouncementRepository.inState(state, now)
				.and(AnnouncementRepository.manageableBy(actor.id(), scope.isAll() ? null : scope.departmentIds()));
		}
		var page = announcementRepository.findAll(spec, pageable);
		List<Long> ids = page.getContent().stream().map(Announcement::getId).toList();
		Map<Long, Receipt> receipts = readRepository.receipts(actor.id(), ids);
		Map<Long, Counts> counts = readRepository.counts(ids);
		Audience audience = audienceSizes();
		return PageResponse.of(page.map(a -> toItem(a, now, actor, scope, receipts.get(a.getId()),
				canManage(a, actor, scope) ? stats(a, counts, audience) : null)));
	}

	@Transactional(readOnly = true)
	public UnreadCount unreadCount(AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		List<Announcement> active = announcementRepository.findAll(AnnouncementRepository
			.inState(AnnouncementState.ACTIVE, calendar.now())
			.and(AnnouncementRepository.audience(audienceOf(actor, scope))));
		Map<Long, Receipt> receipts = readRepository.receipts(actor.id(),
				active.stream().map(Announcement::getId).toList());
		long unread = active.stream().filter(a -> !receipts.containsKey(a.getId())).count();
		long awaitingAck = active.stream()
			.filter(a -> a.isAckRequired()
					&& (!receipts.containsKey(a.getId()) || receipts.get(a.getId()).acknowledgedAt() == null))
			.count();
		return new UnreadCount(unread, awaitingAck);
	}

	@Transactional
	public AnnouncementItem create(Save request, AuthenticatedUser actor, ClientInfo client) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		Announcement announcement = new Announcement();
		announcement.setCreatedBy(userRepository.getReferenceById(actor.id()));
		apply(announcement, request, scope);
		Announcement saved = announcementRepository.save(announcement);
		Instant now = calendar.now();
		boolean published = saved.stateAt(now) == AnnouncementState.ACTIVE;
		auditService.record(AuditAction.ANNOUNCEMENT_PUBLISHED, actor.id(), "ANNOUNCEMENT", saved.getId(),
				Map.of("title", saved.getTitle(), "target",
						saved.getTargetDepartment() == null ? "everyone" : saved.getTargetDepartment().getCode(),
						"scheduled", !published),
				client);
		if (published) {
			notifyAudience(saved, actor);
		}
		return toItem(saved, now, actor, scope, null, new Stats(audienceSizes().of(saved), 0, 0));
	}

	@Transactional
	public AnnouncementItem update(Long id, Save request, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		Announcement announcement = manageable(id, actor, scope);
		if (!Objects.equals(announcement.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this announcement just now. Reload and try again.");
		}
		apply(announcement, request, scope);
		announcementRepository.flush();
		Map<Long, Counts> counts = readRepository.counts(List.of(id));
		return toItem(announcement, calendar.now(), actor, scope, readRepository.receipts(actor.id(), List.of(id)).get(id),
				stats(announcement, counts, audienceSizes()));
	}

	@Transactional
	public void delete(Long id, AuthenticatedUser actor) {
		announcementRepository.delete(manageable(id, actor, accessScopeService.scopeFor(actor)));
	}

	@Transactional
	public void markRead(Long id, AuthenticatedUser actor) {
		readRepository.markRead(loadForAudience(id, actor).getId(), actor.id(), calendar.now());
	}

	@Transactional
	public void acknowledge(Long id, AuthenticatedUser actor) {
		Announcement announcement = loadForAudience(id, actor);
		if (!announcement.isAckRequired()) {
			throw ApiException.badRequest("ACK_NOT_REQUIRED", "This announcement does not need an acknowledgement");
		}
		readRepository.acknowledge(announcement.getId(), actor.id(), calendar.now());
	}

	// --- helpers --------------------------------------------------------------------------------------------

	/** Departments whose announcements the actor receives; {@code null} = all (Super Admin). */
	static Set<Long> audienceOf(AuthenticatedUser actor, AccessScope scope) {
		if (scope.isAll()) {
			return null;
		}
		Set<Long> ids = new HashSet<>(scope.departmentIds());
		ids.add(actor.departmentId());
		return ids;
	}

	static boolean canManage(Announcement announcement, AuthenticatedUser actor, AccessScope scope) {
		if (!actor.hasPermission(ANNOUNCEMENT_MANAGE)) {
			return false;
		}
		return scope.isAll() || announcement.isCreatedBy(actor.id()) || (announcement.getTargetDepartment() != null
				&& scope.coversDepartment(announcement.getTargetDepartment().getId()));
	}

	private void apply(Announcement announcement, Save request, AccessScope scope) {
		Department target = null;
		if (request.targetDepartmentId() == null) {
			if (!scope.isAll()) {
				throw ApiException.forbidden("FORBIDDEN", "Only a Super Admin can address everyone");
			}
		}
		else {
			if (!scope.coversDepartment(request.targetDepartmentId())) {
				throw ApiException.forbidden("FORBIDDEN", "You can only address departments you manage");
			}
			target = departmentRepository.findById(request.targetDepartmentId())
				.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		}
		Instant publishAt = request.publishAt() == null ? calendar.now() : request.publishAt();
		if (request.expiresAt() != null && !request.expiresAt().isAfter(publishAt)) {
			throw ApiException.badRequest("INVALID_DATES", "The expiry must be after the publish time");
		}
		announcement.setTitle(request.title().trim());
		announcement.setBody(request.body().trim());
		announcement.setTargetDepartment(target);
		announcement.setPriority(request.priority());
		announcement.setPublishAt(publishAt);
		announcement.setExpiresAt(request.expiresAt());
		announcement.setAckRequired(request.requiresAck());
	}

	private Announcement manageable(Long id, AuthenticatedUser actor, AccessScope scope) {
		return announcementRepository.findDetailedById(id)
			.filter(a -> canManage(a, actor, scope))
			.orElseThrow(() -> ApiException.notFound("ANNOUNCEMENT_NOT_FOUND", "Announcement not found"));
	}

	/** An active announcement addressed to the actor; anything else is not found. */
	private Announcement loadForAudience(Long id, AuthenticatedUser actor) {
		AccessScope scope = accessScopeService.scopeFor(actor);
		Set<Long> audience = audienceOf(actor, scope);
		return announcementRepository.findDetailedById(id)
			.filter(a -> a.stateAt(calendar.now()) == AnnouncementState.ACTIVE)
			.filter(a -> audience == null || a.getTargetDepartment() == null
					|| audience.contains(a.getTargetDepartment().getId()))
			.orElseThrow(() -> ApiException.notFound("ANNOUNCEMENT_NOT_FOUND", "Announcement not found"));
	}

	private void notifyAudience(Announcement announcement, AuthenticatedUser actor) {
		var spec = UserSpecifications.withStatus(UserStatus.ACTIVE)
			.and(UserSpecifications.inDepartment(
					announcement.getTargetDepartment() == null ? null : announcement.getTargetDepartment().getId()));
		for (User user : userRepository.findAll(spec)) {
			if (!user.getId().equals(actor.id())) {
				notificationService.notify(user.getId(), NotificationType.ANNOUNCEMENT_PUBLISHED,
						announcement.getTitle(), announcement.isAckRequired() ? "Please read and acknowledge" : null,
						NotificationService.ENTITY_ANNOUNCEMENT, announcement.getId(), null);
			}
		}
	}

	private AnnouncementItem toItem(Announcement a, Instant now, AuthenticatedUser actor, AccessScope scope,
			Receipt receipt, Stats stats) {
		return new AnnouncementItem(a.getId(), a.getTitle(), a.getBody(), a.getPriority(),
				a.getTargetDepartment() == null ? null : DepartmentSummary.of(a.getTargetDepartment()), a.getPublishAt(),
				a.getExpiresAt(), a.isAckRequired(), UserSummary.of(a.getCreatedBy()), a.stateAt(now), receipt != null,
				receipt != null && receipt.acknowledgedAt() != null, stats, canManage(a, actor, scope),
				a.getVersion());
	}

	private static Stats stats(Announcement a, Map<Long, Counts> counts, Audience audience) {
		Counts c = counts.getOrDefault(a.getId(), Counts.EMPTY);
		return new Stats(audience.of(a), c.read(), c.acknowledged());
	}

	/** Active users per department and in total, for audience sizes. */
	private Audience audienceSizes() {
		Map<Long, Long> byDepartment = new HashMap<>();
		long total = 0;
		for (DepartmentCount count : userRepository.countByDepartment(UserStatus.ACTIVE)) {
			byDepartment.put(count.getDepartmentId(), count.getTotal());
			total += count.getTotal();
		}
		return new Audience(byDepartment, total);
	}

	private record Audience(Map<Long, Long> byDepartment, long total) {

		long of(Announcement announcement) {
			return announcement.getTargetDepartment() == null ? total
					: byDepartment.getOrDefault(announcement.getTargetDepartment().getId(), 0L);
		}

	}

}
