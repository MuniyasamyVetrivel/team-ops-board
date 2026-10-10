package com.teamops.admin.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.admin.dto.AuditLogDtos.ActionInfo;
import com.teamops.admin.dto.AuditLogDtos.AuditLogItem;
import com.teamops.admin.dto.AuditLogDtos.Catalog;
import com.teamops.admin.dto.AuditLogDtos.EventInfo;
import com.teamops.admin.dto.AuditLogDtos.Filter;
import com.teamops.admin.dto.AuditLogDtos.ModuleInfo;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditCatalog;
import com.teamops.common.audit.AuditLog;
import com.teamops.common.audit.AuditLogRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.report.ReportDocument;
import com.teamops.common.web.PageResponse;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Audit log viewer: filtered, paged search over every audited action, the filter catalogue and CSV export. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

	/** Exports stop here; narrow the filters for more. */
	static final int MAX_EXPORT_ROWS = 5000;

	private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final AuditLogRepository auditLogRepository;

	private final UserRepository userRepository;

	private final BusinessCalendar calendar;

	private final ObjectMapper objectMapper;

	public PageResponse<AuditLogItem> search(Filter filter, Pageable pageable) {
		Page<AuditLog> page = auditLogRepository.findAll(spec(filter), pageable);
		Map<Long, User> actors = actors(page.getContent());
		return PageResponse.of(page.map(log -> toItem(log, actors)));
	}

	public Catalog catalog() {
		List<ModuleInfo> modules = Arrays.stream(AuditCatalog.Module.values())
			.map(m -> new ModuleInfo(m.name(), m.label()))
			.toList();
		List<ActionInfo> actions = Arrays.stream(AuditAction.values())
			.map(a -> new ActionInfo(a.name(), AuditCatalog.labelOf(a), AuditCatalog.moduleOf(a).name(),
					AuditCatalog.eventsOf(a).stream().map(Enum::name).toList()))
			.toList();
		List<EventInfo> events = Arrays.stream(AuditCatalog.BriefEvent.values())
			.map(e -> new EventInfo(e.name(), e.label(), e.actions().stream().map(Enum::name).sorted().toList()))
			.toList();
		List<UserSummary> actors = userRepository.findAllById(auditLogRepository.findActorIds())
			.stream()
			.sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
			.map(UserSummary::of)
			.toList();
		return new Catalog(modules, actions, events, auditLogRepository.findEntityTypes(), actors);
	}

	/** The newest {@link #MAX_EXPORT_ROWS} matching entries (or as sorted by {@code sort}). */
	public ReportDocument export(Filter filter, Pageable sort) {
		Page<AuditLog> page = auditLogRepository.findAll(spec(filter),
				PageRequest.of(0, MAX_EXPORT_ROWS, sort.getSort()));
		Map<Long, User> actors = actors(page.getContent());
		List<List<String>> rows = new ArrayList<>();
		for (AuditLog log : page.getContent()) {
			User actor = log.getActorId() == null ? null : actors.get(log.getActorId());
			AuditAction action = AuditCatalog.parse(log.getAction()).orElse(null);
			rows.add(List.of(EXPORT_TIME.format(log.getCreatedAt().atZone(calendar.zone())), log.getAction(),
					action == null ? "" : AuditCatalog.moduleOf(action).label(),
					actor == null ? (log.getActorId() == null ? "System" : "User #" + log.getActorId())
							: actor.getFullName(),
					actor == null ? "" : actor.getEmail(), Objects.toString(log.getEntityType(), ""),
					log.getEntityId() == null ? "" : log.getEntityId().toString(),
					Objects.toString(log.getDetails(), ""), Objects.toString(log.getIpAddress(), "")));
		}
		String subtitle = "Times in " + calendar.zone().getId() + ". "
				+ (page.getTotalElements() > MAX_EXPORT_ROWS
						? "First " + MAX_EXPORT_ROWS + " of " + page.getTotalElements() + " matching entries."
						: page.getTotalElements() + " matching entries.");
		return new ReportDocument("Audit log", subtitle, "audit-log-" + calendar.today(),
				List.of(new ReportDocument.Section("Entries", List.of("Time", "Action", "Module", "Actor",
						"Actor email", "Entity type", "Entity id", "Details", "IP address"), rows)));
	}

	// --- filters ----------------------------------------------------------------------------------------------

	Specification<AuditLog> spec(Filter filter) {
		if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
			throw ApiException.badRequest("INVALID_RANGE", "The start date must be on or before the end date");
		}
		Set<AuditAction> actions = actionsFor(filter);
		return (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (actions != null) {
				if (actions.isEmpty()) {
					return cb.disjunction();
				}
				where.add(root.get("action").in(actions.stream().map(Enum::name).toList()));
			}
			if (filter.actorId() != null) {
				where.add(cb.equal(root.get("actorId"), filter.actorId()));
			}
			if (StringUtils.hasText(filter.entityType())) {
				where.add(cb.equal(root.get("entityType"), filter.entityType().trim()));
			}
			if (filter.entityId() != null) {
				where.add(cb.equal(root.get("entityId"), filter.entityId()));
			}
			if (filter.from() != null) {
				where.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), calendar.startOf(filter.from())));
			}
			if (filter.to() != null) {
				where.add(cb.lessThan(root.<Instant>get("createdAt"), calendar.startOf(filter.to().plusDays(1))));
			}
			if (StringUtils.hasText(filter.search())) {
				String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
					.replace("%", "\\%")
					.replace("_", "\\_") + "%";
				Subquery<Long> people = query.subquery(Long.class);
				Root<User> user = people.from(User.class);
				people.select(user.get("id"))
					.where(cb.or(cb.like(cb.lower(user.get("email")), pattern, '\\'),
							cb.like(cb.lower(cb.concat(cb.concat(user.get("firstName"), " "), user.get("lastName"))),
									pattern, '\\')));
				where.add(cb.or(cb.like(cb.lower(root.<String>get("details")), pattern, '\\'),
						cb.like(cb.lower(root.get("action")), pattern, '\\'),
						cb.like(cb.lower(root.get("entityType")), pattern, '\\'),
						cb.like(root.get("ipAddress"), pattern, '\\'), root.get("actorId").in(people)));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	/** The actions the filter allows, or null when it does not restrict them. */
	static Set<AuditAction> actionsFor(Filter filter) {
		Set<AuditAction> allowed = null;
		if (!filter.actions().isEmpty()) {
			allowed = EnumSet.copyOf(filter.actions());
		}
		if (filter.module() != null) {
			allowed = intersect(allowed, AuditCatalog.actionsIn(filter.module()));
		}
		if (filter.event() != null) {
			allowed = intersect(allowed, filter.event().actions());
		}
		return allowed;
	}

	private static Set<AuditAction> intersect(Set<AuditAction> current, Set<AuditAction> with) {
		Set<AuditAction> result = EnumSet.noneOf(AuditAction.class);
		result.addAll(with);
		if (current != null) {
			result.retainAll(current);
		}
		return result;
	}

	// --- mapping ----------------------------------------------------------------------------------------------

	private Map<Long, User> actors(List<AuditLog> logs) {
		Set<Long> ids = logs.stream().map(AuditLog::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
		return ids.isEmpty() ? Map.of()
				: userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
	}

	private AuditLogItem toItem(AuditLog log, Map<Long, User> actors) {
		AuditAction action = AuditCatalog.parse(log.getAction()).orElse(null);
		AuditCatalog.Module module = action == null ? null : AuditCatalog.moduleOf(action);
		User actor = log.getActorId() == null ? null : actors.get(log.getActorId());
		return new AuditLogItem(log.getId(), log.getAction(),
				action == null ? log.getAction() : AuditCatalog.labelOf(action), module == null ? null : module.name(),
				module == null ? null : module.label(), UserSummary.of(actor), log.getEntityType(), log.getEntityId(),
				details(log.getDetails()), log.getIpAddress(), log.getUserAgent(), log.getCreatedAt());
	}

	private Object details(String json) {
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, Object.class);
		}
		catch (JacksonException ex) {
			return json;
		}
	}

}
