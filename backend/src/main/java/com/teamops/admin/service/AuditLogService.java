package com.teamops.admin.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import com.teamops.admin.dto.AuditLogDtos.RecordTypeInfo;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditCatalog;
import com.teamops.common.audit.AuditDetails;
import com.teamops.common.audit.AuditLog;
import com.teamops.common.audit.AuditLogRepository;
import com.teamops.common.audit.RecordTypeCatalog;
import com.teamops.common.audit.RecordTypes;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.report.ReportDocument;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.FinanceAccess;
import com.teamops.common.web.PageResponse;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Audit log viewer: filtered, paged search over every audited action, the filter catalogue and CSV export; and the
 * history of one record (its own entries and its children's). Money amounts are hidden from viewers who cannot see
 * finance ({@link AuditDetails}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

	/** Exports stop here; narrow the filters for more. */
	static final int MAX_EXPORT_ROWS = 5000;

	private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final String USER_TYPE = RecordTypes.typeOf(User.class);

	private static final String DEPARTMENT_TYPE = RecordTypes.typeOf(Department.class);

	/**
	 * What a Finance Tracker entry's details read as for viewers without FINANCE_VIEW: its records are finance data as a
	 * whole (brief X8), so the details and the record's name are left out, not just the amounts.
	 */
	static final Map<String, Object> FINANCE_HIDDEN = Map.of("hidden", true, "reason", "FINANCE_VIEW");

	private final AuditLogRepository auditLogRepository;

	private final UserRepository userRepository;

	private final DepartmentRepository departmentRepository;

	private final RecordTypeCatalog recordTypes;

	private final BusinessCalendar calendar;

	private final ObjectMapper objectMapper;

	public PageResponse<AuditLogItem> search(Filter filter, Pageable pageable, AuthenticatedUser viewer) {
		boolean finance = FinanceAccess.canView(viewer);
		Page<AuditLog> page = auditLogRepository.findAll(spec(filter, finance), pageable);
		return page(page, toItems(page.getContent(), finance, true));
	}

	/**
	 * A record's timeline, newest first: its own entries and those of the records that belong to it. The caller has
	 * checked that the viewer may see the record. IP addresses are left out unless the viewer reads the audit log.
	 */
	public PageResponse<AuditLogItem> history(String entityType, Long entityId, Pageable pageable,
			AuthenticatedUser viewer) {
		Specification<AuditLog> spec = (root, query, cb) -> cb.or(
				cb.and(cb.equal(root.get("entityType"), entityType), cb.equal(root.get("entityId"), entityId)),
				cb.and(cb.equal(root.get("parentType"), entityType), cb.equal(root.get("parentId"), entityId)));
		Page<AuditLog> page = auditLogRepository.findAll(spec, PageRequest.of(pageable.getPageNumber(),
				pageable.getPageSize(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		return page(page, toItems(page.getContent(), FinanceAccess.canView(viewer), viewer.hasPermission(PermissionCodes.AUDIT_VIEW)));
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
		List<RecordTypeInfo> types = recordTypes.all()
			.stream()
			.map(t -> new RecordTypeInfo(t.type(), t.label(), t.module() == null ? null : t.module().name()))
			.toList();
		List<UserSummary> actors = userRepository.findAllById(auditLogRepository.findActorIds())
			.stream()
			.sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
			.map(UserSummary::of)
			.toList();
		return new Catalog(modules, actions, events, auditLogRepository.findEntityTypes(), types, actors);
	}

	/** The newest {@link #MAX_EXPORT_ROWS} matching entries (or as sorted by {@code sort}). */
	public ReportDocument export(Filter filter, Pageable sort, AuthenticatedUser viewer) {
		boolean finance = FinanceAccess.canView(viewer);
		Page<AuditLog> page = auditLogRepository.findAll(spec(filter, finance),
				PageRequest.of(0, MAX_EXPORT_ROWS, sort.getSort()));
		List<List<String>> rows = new ArrayList<>();
		for (AuditLogItem item : toItems(page.getContent(), finance, true)) {
			UserSummary actor = item.actor();
			rows.add(List.of(EXPORT_TIME.format(item.createdAt().atZone(calendar.zone())), item.action(),
					item.actionLabel(), Objects.toString(item.moduleLabel(), ""),
					actor == null ? "System" : actor.fullName(), actor == null ? "" : Objects.toString(actor.email(), ""),
					Objects.toString(item.entityType(), ""), item.entityId() == null ? "" : item.entityId().toString(),
					Objects.toString(item.entityLabel(), ""),
					item.parentType() == null ? "" : item.parentType() + " #" + item.parentId(), json(item.details()),
					Objects.toString(item.ipAddress(), "")));
		}
		String subtitle = "Times in " + calendar.zone().getId() + ". "
				+ (page.getTotalElements() > MAX_EXPORT_ROWS
						? "First " + MAX_EXPORT_ROWS + " of " + page.getTotalElements() + " matching entries."
						: page.getTotalElements() + " matching entries.")
				+ (finance ? "" : " Finance amounts are hidden.");
		return new ReportDocument("Audit log", subtitle, "audit-log-" + calendar.today(),
				List.of(new ReportDocument.Section("Entries", List.of("Time", "Action", "Description", "Module",
						"Actor", "Actor email", "Entity type", "Entity id", "Record", "Belongs to", "Details",
						"IP address"), rows)));
	}

	// --- filters ----------------------------------------------------------------------------------------------

	Specification<AuditLog> spec(Filter filter, boolean canSeeFinance) {
		if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
			throw ApiException.badRequest("INVALID_RANGE", "The start date must be on or before the end date");
		}
		Set<AuditAction> actions = actionsFor(filter);
		Set<AuditAction> recordActions = recordActionsFor(filter);
		Set<String> moduleTypes = recordActions.isEmpty() ? Set.of() : recordTypes.typesIn(filter.module());
		return (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (actions != null) {
				List<Predicate> anyOf = new ArrayList<>();
				if (!actions.isEmpty()) {
					anyOf.add(root.get("action").in(actions.stream().map(Enum::name).toList()));
				}
				if (!moduleTypes.isEmpty()) {
					anyOf.add(cb.and(root.get("action").in(recordActions.stream().map(Enum::name).toList()),
							root.get("entityType").in(moduleTypes)));
				}
				if (anyOf.isEmpty()) {
					return cb.disjunction();
				}
				where.add(cb.or(anyOf.toArray(Predicate[]::new)));
			}
			if (filter.actorId() != null) {
				where.add(cb.equal(root.get("actorId"), filter.actorId()));
			}
			if (StringUtils.hasText(filter.entityType()) && filter.entityId() != null) {
				String type = filter.entityType().trim();
				where.add(cb.or(
						cb.and(cb.equal(root.get("entityType"), type), cb.equal(root.get("entityId"), filter.entityId())),
						cb.and(cb.equal(root.get("parentType"), type),
								cb.equal(root.get("parentId"), filter.entityId()))));
			}
			else if (StringUtils.hasText(filter.entityType())) {
				where.add(cb.equal(root.get("entityType"), filter.entityType().trim()));
			}
			else if (filter.entityId() != null) {
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
				Predicate details = cb.like(cb.lower(root.<String>get("details")), pattern, '\\');
				Predicate label = cb.like(cb.lower(root.get("entityLabel")), pattern, '\\');
				if (!canSeeFinance) {
					// Searching amounts would reveal them: details holding finance amounts are not searched.
					details = cb.and(cb.isFalse(root.get("finance")), details);
					// Finance Tracker records are finance data as a whole: their names and details are not searched.
					Set<String> financeTypes = recordTypes.typesIn(AuditCatalog.Module.FINANCE);
					if (!financeTypes.isEmpty()) {
						Predicate notFinance = cb.not(root.get("entityType").in(financeTypes));
						details = cb.and(notFinance, details);
						label = cb.and(notFinance, label);
					}
				}
				where.add(cb.or(details, cb.like(cb.lower(root.get("action")), pattern, '\\'),
						cb.like(cb.lower(root.get("entityType")), pattern, '\\'), label,
						cb.like(root.get("ipAddress"), pattern, '\\'), root.get("actorId").in(people)));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	/** The business actions the filter allows, or null when it does not restrict them. */
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

	/**
	 * Record changes a module filter matches through its record types (a task edit is in the Tasks module); none
	 * without a module, for the Record changes module itself (its actions match directly) or with a brief event.
	 */
	static Set<AuditAction> recordActionsFor(Filter filter) {
		if (filter.module() == null || filter.module() == AuditCatalog.Module.RECORDS || filter.event() != null) {
			return Set.of();
		}
		Set<AuditAction> allowed = EnumSet.copyOf(AuditCatalog.RECORD_ACTIONS);
		if (!filter.actions().isEmpty()) {
			allowed.retainAll(filter.actions());
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

	private List<AuditLogItem> toItems(List<AuditLog> logs, boolean canSeeFinance, boolean withClient) {
		Map<Long, User> actors = users(
				logs.stream().map(AuditLog::getActorId).filter(Objects::nonNull).collect(Collectors.toSet()));
		List<Object> details = new ArrayList<>(logs.size());
		Set<Long> userRefs = new HashSet<>();
		Set<Long> departmentRefs = new HashSet<>();
		for (AuditLog log : logs) {
			Object parsed = AuditDetails.forViewer(parse(log.getDetails()),
					AuditCatalog.parse(log.getAction()).orElse(null), canSeeFinance);
			details.add(parsed);
			collectRefs(parsed, USER_TYPE, userRefs);
			collectRefs(parsed, DEPARTMENT_TYPE, departmentRefs);
		}
		Map<String, Map<Long, String>> labels = Map.of(USER_TYPE,
				users(userRefs).values().stream().collect(Collectors.toMap(User::getId, User::getFullName)),
				DEPARTMENT_TYPE, departmentRefs.isEmpty() ? Map.of()
						: departmentRepository.findAllById(departmentRefs)
							.stream()
							.collect(Collectors.toMap(Department::getId, Department::getName)));
		List<AuditLogItem> items = new ArrayList<>(logs.size());
		for (int i = 0; i < logs.size(); i++) {
			AuditLog log = logs.get(i);
			Object parsed = details.get(i);
			addRefLabels(parsed, labels);
			// System entries have no actor (and Map.of() rejects a null key).
			User actor = log.getActorId() == null ? null : actors.get(log.getActorId());
			items.add(toItem(log, actor, parsed, withClient, canSeeFinance));
		}
		return items;
	}

	private AuditLogItem toItem(AuditLog log, User actor, Object details, boolean withClient, boolean canSeeFinance) {
		AuditAction action = AuditCatalog.parse(log.getAction()).orElse(null);
		AuditCatalog.Module module = action == null ? null
				: AuditCatalog.isRecordChange(action) && recordTypes.moduleOf(log.getEntityType()) != null
						? recordTypes.moduleOf(log.getEntityType()) : AuditCatalog.moduleOf(action);
		boolean hidden = module == AuditCatalog.Module.FINANCE && !canSeeFinance;
		return new AuditLogItem(log.getId(), log.getAction(),
				action == null ? log.getAction() : AuditCatalog.labelOf(action, log.getEntityType()),
				module == null ? null : module.name(), module == null ? null : module.label(), UserSummary.of(actor),
				log.getEntityType(), RecordTypes.labelOf(log.getEntityType()), log.getEntityId(),
				hidden ? null : log.getEntityLabel(), log.getParentType(), log.getParentId(),
				hidden ? FINANCE_HIDDEN : details, withClient ? log.getIpAddress() : null,
				withClient ? log.getUserAgent() : null, log.getCreatedAt());
	}

	private Map<Long, User> users(Set<Long> ids) {
		return ids.isEmpty() ? Map.of()
				: userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
	}

	/** Ids of {@code type} referenced by record changes ({@code ref}). */
	private static void collectRefs(Object details, String type, Set<Long> into) {
		forEachRef(details, type, change -> {
			idsOf(change.get("from"), into);
			idsOf(change.get("to"), into);
		});
	}

	/** Puts the names of referenced users and departments next to their ids. */
	private static void addRefLabels(Object details, Map<String, Map<Long, String>> labels) {
		labels.forEach((type, names) -> forEachRef(details, type, change -> {
			if (change.get("from") != null) {
				change.put("fromLabel", label(change.get("from"), names));
			}
			if (change.get("to") != null) {
				change.put("toLabel", label(change.get("to"), names));
			}
		}));
	}

	@SuppressWarnings("unchecked")
	private static void forEachRef(Object details, String type, Consumer<Map<String, Object>> action) {
		if (details instanceof Map<?, ?> map && map.get("changes") instanceof Map<?, ?> changes) {
			for (Object change : changes.values()) {
				if (change instanceof Map<?, ?> fields && type.equals(fields.get("ref"))) {
					action.accept((Map<String, Object>) fields);
				}
			}
		}
	}

	private static void idsOf(Object value, Set<Long> into) {
		if (value instanceof Number number) {
			into.add(number.longValue());
		}
		else if (value instanceof Collection<?> values) {
			values.forEach(v -> idsOf(v, into));
		}
	}

	private static Object label(Object value, Map<Long, String> names) {
		if (value instanceof Number number) {
			return names.getOrDefault(number.longValue(), "#" + number);
		}
		if (value instanceof Collection<?> values) {
			return values.stream().map(v -> label(v, names)).toList();
		}
		return value;
	}

	private static PageResponse<AuditLogItem> page(Page<AuditLog> page, List<AuditLogItem> items) {
		return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	private Object parse(String json) {
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

	private String json(Object details) {
		return details == null ? "" : details instanceof String text ? text : objectMapper.writeValueAsString(details);
	}

}
