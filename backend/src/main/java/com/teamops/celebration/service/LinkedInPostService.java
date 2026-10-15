package com.teamops.celebration.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.celebration.dto.CelebrationDtos.LinkedInPostDto;
import com.teamops.celebration.dto.CelebrationDtos.LinkedInPostRow;
import com.teamops.celebration.dto.CelebrationDtos.SaveLinkedInPost;
import com.teamops.celebration.entity.LinkedInPost;
import com.teamops.celebration.entity.LinkedInPostStatus;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.repository.LinkedInPostRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * LinkedIn post tracking for birthdays, work anniversaries, important days and events. A post belongs to one
 * occurrence (subject and date); when a subject's date moves, its upcoming posts move with it.
 */
@Service
@RequiredArgsConstructor
public class LinkedInPostService {

	public static final String ENTITY = "LINKEDIN_POST";

	/** Default list window: today and the next 60 days. */
	public static final int DEFAULT_DAYS = 60;

	public static final int MAX_RANGE_DAYS = 366;

	private final LinkedInPostRepository repository;

	private final OccasionFinder finder;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	/** The saved posts of these occasions, by {@link Occasion#key()}. */
	@Transactional(readOnly = true)
	public Map<String, LinkedInPost> forOccasions(Collection<Occasion> occasions) {
		if (occasions.isEmpty()) {
			return Map.of();
		}
		LocalDate from = occasions.stream().map(Occasion::date).min(LocalDate::compareTo).orElseThrow();
		LocalDate to = occasions.stream().map(Occasion::date).max(LocalDate::compareTo).orElseThrow();
		Map<String, LinkedInPost> posts = new HashMap<>();
		repository.findByOccurrenceDateBetween(from, to)
			.forEach(post -> posts.put(Occasion.key(post.getSubjectType(), post.getSubjectId(), post.getOccurrenceDate()),
					post));
		return posts;
	}

	/** Every saved post of these subjects (all years), by {@link Occasion#key()}. */
	@Transactional(readOnly = true)
	public Map<String, LinkedInPost> forSubjects(OccasionKind kind, Collection<Long> subjectIds) {
		Map<String, LinkedInPost> posts = new HashMap<>();
		if (!subjectIds.isEmpty()) {
			repository.findBySubjectTypeAndSubjectIdIn(kind, subjectIds)
				.forEach(post -> posts.put(Occasion.key(kind, post.getSubjectId(), post.getOccurrenceDate()), post));
		}
		return posts;
	}

	/** The tracking of one occurrence: the saved post, or the kind's default when there is none. */
	public static LinkedInPostDto dto(OccasionKind kind, LinkedInPost post) {
		if (post == null) {
			return new LinkedInPostDto(kind.postNeededByDefault(), false, LinkedInPostStatus.NOT_STARTED, null, null,
					null, null);
		}
		return new LinkedInPostDto(post.isPostNeeded(), true, post.getStatus(), UserSummary.of(post.getOwner()),
				post.getPostDate(), post.getPostLink(), post.getVersion());
	}

	@Transactional(readOnly = true)
	public LinkedInPostDto forOccurrence(OccasionKind kind, Long subjectId, LocalDate date) {
		return dto(kind, repository.findBySubjectTypeAndSubjectIdAndOccurrenceDate(kind, subjectId, date).orElse(null));
	}

	/**
	 * Everything coming up in [from, to] (today and the next {@value #DEFAULT_DAYS} days by default) with its post
	 * status, earliest first. Cancelled events are left out.
	 * @param needed only occurrences that need (true) or do not need (false) a post
	 */
	@Transactional(readOnly = true)
	public PageResponse<LinkedInPostRow> list(LocalDate from, LocalDate to, Set<LinkedInPostStatus> statuses,
			Boolean needed, Set<OccasionKind> kinds, Pageable pageable) {
		LocalDate start = from == null ? calendar.today() : from;
		LocalDate end = to == null ? start.plusDays(DEFAULT_DAYS) : to;
		if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
			throw ApiException.badRequest("INVALID_RANGE",
					"'to' must be on or after 'from' and the range at most " + MAX_RANGE_DAYS + " days");
		}
		List<Occasion> occasions = finder.between(start, end, false)
			.stream()
			.filter(o -> !o.date().isBefore(start))
			.filter(o -> kinds == null || kinds.isEmpty() || kinds.contains(o.kind()))
			.toList();
		Map<String, LinkedInPost> posts = forOccasions(occasions);
		List<LinkedInPostRow> rows = occasions.stream()
			.map(o -> new LinkedInPostRow(o.key(), o.kind(), o.subjectId(), o.category(), o.title(), o.date(),
					o.region(), dto(o.kind(), posts.get(o.key()))))
			.filter(row -> needed == null || row.linkedIn().needed() == needed)
			.filter(row -> statuses == null || statuses.isEmpty() || statuses.contains(row.linkedIn().status()))
			.toList();
		int first = (int) Math.min(pageable.getOffset(), rows.size());
		int last = Math.min(first + pageable.getPageSize(), rows.size());
		return PageResponse.of(new PageImpl<>(rows.subList(first, last), pageable, rows.size()));
	}

	/**
	 * Saves the tracking of an occurrence that exists (an active employee's birthday or anniversary on that date, the
	 * important day on that date, or the event starting that day). Audited.
	 */
	@Transactional
	public LinkedInPostDto save(SaveLinkedInPost request, AuthenticatedUser actor, ClientInfo client) {
		Occasion occasion = finder.between(request.occurrenceDate(), request.occurrenceDate(), true)
			.stream()
			.filter(o -> o.kind() == request.subjectType() && o.subjectId().equals(request.subjectId())
					&& o.date().equals(request.occurrenceDate()))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("OCCURRENCE_NOT_FOUND",
					"Nothing of this kind falls on " + request.occurrenceDate()));
		String link = CelebrationText.link(request.postLink(), "Post link");
		User owner = request.ownerId() == null ? null : userRepository.findById(request.ownerId())
			.filter(User::isActive)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_USER", "The post owner must be an active user"));

		LinkedInPost post = repository
			.findBySubjectTypeAndSubjectIdAndOccurrenceDate(occasion.kind(), occasion.subjectId(), occasion.date())
			.orElse(null);
		boolean created = post == null;
		if (created) {
			if (request.version() != null) {
				throw stale();
			}
			post = new LinkedInPost();
			post.setSubjectType(occasion.kind());
			post.setSubjectId(occasion.subjectId());
			post.setOccurrenceDate(occasion.date());
		}
		else if (!Objects.equals(post.getVersion(), request.version())) {
			throw stale();
		}
		AuditChanges changes = new AuditChanges()
			.track("postNeeded", created ? null : post.isPostNeeded(), request.postNeeded())
			.track("ownerId", post.getOwner() == null ? null : post.getOwner().getId(), owner == null ? null : owner.getId())
			.track("status", created ? null : post.getStatus(), request.status())
			.track("postDate", post.getPostDate(), request.postDate())
			.track("postLink", post.getPostLink(), link);
		post.setPostNeeded(request.postNeeded());
		post.setOwner(owner);
		post.setStatus(request.status());
		post.setPostDate(request.postDate());
		post.setPostLink(link);
		post.setUpdatedBy(userRepository.getReferenceById(actor.id()));
		LinkedInPost saved = repository.saveAndFlush(post);
		if (!changes.isEmpty()) {
			Map<String, Object> details = new HashMap<>(changes.toDetails());
			details.put("subject", occasion.kind() + " " + occasion.subjectId());
			details.put("occurrenceDate", occasion.date());
			details.put("title", occasion.title());
			auditService.record(AuditAction.LINKEDIN_POST_UPDATED, actor.id(), ENTITY, saved.getId(), details, client);
		}
		return dto(saved.getSubjectType(), saved);
	}

	/**
	 * Moves the subject's posts from {@code today} on to the dates {@code move} gives (null drops a post), after its
	 * date changed. A post that would land on a date that already has one is dropped.
	 */
	@Transactional
	public void relocate(OccasionKind kind, Long subjectId, LocalDate today, UnaryOperator<LocalDate> move) {
		List<LinkedInPost> posts = repository.findBySubjectTypeAndSubjectIdAndOccurrenceDateGreaterThanEqual(kind,
				subjectId, today);
		Set<LocalDate> taken = new HashSet<>();
		for (LinkedInPost post : posts) {
			LocalDate target = move.apply(post.getOccurrenceDate());
			if (target == null || !taken.add(target)) {
				repository.delete(post);
			}
			else {
				post.setOccurrenceDate(target);
			}
		}
		repository.flush();
	}

	@Transactional
	public void deleteFor(Collection<OccasionKind> kinds, Long subjectId) {
		repository.deleteAll(repository.findBySubjectTypeInAndSubjectId(kinds, subjectId));
	}

	private static ApiException stale() {
		return ApiException.conflict("STALE_UPDATE", "Someone else changed this LinkedIn post just now. Reload and try again.");
	}

}
