package com.teamops.recyclebin.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditCatalog;
import com.teamops.common.audit.EntityChangeAuditor;
import com.teamops.common.audit.RecordTypes;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.persistence.SoftDeletableEntity;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.PageResponse;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.recyclebin.dto.RecycleBinDtos.Catalog;
import com.teamops.recyclebin.dto.RecycleBinDtos.DepartmentRef;
import com.teamops.recyclebin.dto.RecycleBinDtos.Filter;
import com.teamops.recyclebin.dto.RecycleBinDtos.Item;
import com.teamops.recyclebin.dto.RecycleBinDtos.ModuleInfo;
import com.teamops.recyclebin.dto.RecycleBinDtos.Restored;
import com.teamops.recyclebin.dto.RecycleBinDtos.TypeInfo;
import com.teamops.recyclebin.entity.RecycleBinItem;
import com.teamops.recyclebin.repository.RecycleBinItemRepository;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.PermissionCodes;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * The recycle bin. Deleting a record moves it here: {@code deleted_at}/{@code deleted_by} are set and the row stays
 * (Hibernate hides it everywhere), so restoring brings it back exactly as it was, with its comments, checklist,
 * attachments and other children, which are never touched. Rules:
 * <ul>
 * <li>Only holders of RECYCLE_BIN_MANAGE (Super Admin only by default) see the bin, restore and delete permanently;
 * a permanent delete is confirmed with the item's name. Anyone else gets 403.</li>
 * <li>Items older than the {@code recycleBin.retentionDays} setting (default 90) are deleted permanently by a daily
 * job.</li>
 * <li>Unique values (an employee ID, a page URL) stay taken until the permanent delete ({@link #requireNotInBin}).</li>
 * </ul>
 * Restore and permanent delete go through the entity, so the record's history logs them like any other change.
 */
@Slf4j
@Service
public class RecycleBinService {

	/** Fields that name a record, in order of preference. */
	private static final List<String> LABEL_PROPERTIES = List.of("code", "title", "name", "fullName", "subject",
			"keyword", "referringDomain", "url");

	private static final int PURGE_BATCH = 200;

	private record BinType(String type, Class<? extends SoftDeletableEntity> entityClass, String table) {

	}

	private final RecycleBinItemRepository items;

	private final EntityManager entityManager;

	private final AppSettingsService settings;

	private final BusinessCalendar calendar;

	private final UserRepository userRepository;

	private final DepartmentRepository departmentRepository;

	private final Clock clock;

	private final EntityChangeAuditor auditor;

	private final Map<String, BinType> types;

	/** Resolved on first use: handlers may depend on module services that depend on this one. */
	private final ObjectProvider<RecycleBinHandler<?>> handlerBeans;

	private volatile Map<Class<?>, RecycleBinHandler<?>> handlers;

	public RecycleBinService(RecycleBinItemRepository items, EntityManager entityManager,
			AppSettingsService settings, BusinessCalendar calendar,
			UserRepository userRepository, DepartmentRepository departmentRepository, Clock clock,
			EntityChangeAuditor auditor, ObjectProvider<RecycleBinHandler<?>> handlers) {
		this.items = items;
		this.entityManager = entityManager;
		this.settings = settings;
		this.calendar = calendar;
		this.userRepository = userRepository;
		this.departmentRepository = departmentRepository;
		this.clock = clock;
		this.auditor = auditor;
		this.handlerBeans = handlers;
		Map<String, BinType> found = new LinkedHashMap<>();
		for (var entity : entityManager.getMetamodel().getEntities()) {
			Class<?> javaType = entity.getJavaType();
			if (SoftDeletableEntity.class.isAssignableFrom(javaType)) {
				@SuppressWarnings("unchecked")
				Class<? extends SoftDeletableEntity> soft = (Class<? extends SoftDeletableEntity>) javaType;
				String type = RecordTypes.typeOf(javaType);
				found.put(type, new BinType(type, soft, javaType.getAnnotation(Table.class).name()));
			}
		}
		this.types = Map.copyOf(found);
	}

	// --- moving into the bin ----------------------------------------------------------------------------------

	/**
	 * Deletes a record into the bin. Called by a module's delete, after its own checks, in its transaction. The
	 * record's history logs the delete (its {@code deletedAt} changes).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void moveToBin(SoftDeletableEntity entity, AuthenticatedUser actor) {
		if (entity.isDeleted()) {
			return;
		}
		Instant now = clock.instant();
		Long actorId = actor == null ? null : actor.id();
		entity.markDeleted(now, actorId);
		Class<?> type = entityClass(entity);
		AuditCatalog.Module module = RecordTypes.moduleOf(type);
		items.save(new RecycleBinItem(RecordTypes.typeOf(type), entity.getId(), label(entity),
				module == null ? AuditCatalog.Module.RECORDS.name() : module.name(), department(entity), now, actorId));
	}

	/**
	 * Stops a new or edited record from taking a unique value a record in the bin still holds; the value is released
	 * only by the permanent delete. {@code columns} are database column names and values.
	 */
	@Transactional(readOnly = true)
	public void requireNotInBin(Class<? extends SoftDeletableEntity> entityClass, Map<String, ?> columns,
			String what) {
		BinType type = typeOf(RecordTypes.typeOf(entityClass));
		StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(type.table()).append(" WHERE deleted_at IS NOT NULL");
		List<Object> values = new ArrayList<>();
		columns.forEach((column, value) -> {
			if (!column.matches("[a-z_]+")) {
				throw new IllegalArgumentException("Not a column name: " + column);
			}
			if (value == null) {
				sql.append(" AND ").append(column).append(" IS NULL");
			}
			else {
				sql.append(" AND ").append(column).append(" = ?");
				values.add(value instanceof Enum<?> e ? e.name() : value);
			}
		});
		var query = entityManager.createNativeQuery(sql.toString());
		for (int i = 0; i < values.size(); i++) {
			query.setParameter(i + 1, values.get(i));
		}
		if (((Number) query.getSingleResult()).longValue() > 0) {
			throw ApiException.conflict("IN_RECYCLE_BIN", what + " belongs to a deleted " + RecordTypes.labelOf(type.type())
				.toLowerCase(Locale.ROOT) + " in the recycle bin. Restore it, or ask a Super Admin to delete it permanently.");
		}
	}

	/** Whether a record is in the bin (for restore checks of records that belong to another one). */
	@Transactional(readOnly = true)
	public boolean isInBin(Class<? extends SoftDeletableEntity> entityClass, Long id) {
		return id != null && items.findByEntityTypeAndEntityId(RecordTypes.typeOf(entityClass), id).isPresent();
	}

	// --- reading ----------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<Item> list(Filter filter, Pageable pageable, AuthenticatedUser viewer) {
		requireBinAccess(viewer);
		if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
			throw ApiException.badRequest("INVALID_RANGE", "The start date must be on or before the end date");
		}
		Page<RecycleBinItem> page = items.findAll(spec(filter), pageable);
		return new PageResponse<>(toItems(page.getContent()), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	@Transactional(readOnly = true)
	public Catalog catalog(AuthenticatedUser viewer) {
		requireBinAccess(viewer);
		List<TypeInfo> typeInfos = types.values()
			.stream()
			.map(t -> {
				AuditCatalog.Module module = RecordTypes.moduleOf(t.entityClass());
				return new TypeInfo(t.type(), RecordTypes.labelOf(t.type()), module == null ? null : module.name());
			})
			.sorted(Comparator.comparing(TypeInfo::label))
			.toList();
		List<ModuleInfo> modules = typeInfos.stream()
			.map(TypeInfo::module)
			.filter(Objects::nonNull)
			.distinct()
			.map(AuditCatalog.Module::valueOf)
			.sorted(Comparator.comparing(AuditCatalog.Module::label))
			.map(m -> new ModuleInfo(m.name(), m.label()))
			.toList();
		List<UserSummary> deleters = userRepository.findAllById(items.findDeleterIds())
			.stream()
			.sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
			.map(UserSummary::of)
			.toList();
		return new Catalog(modules, typeInfos, deleters, settings.recycleBinRetentionDays());
	}

	// --- restore and permanent delete ---------------------------------------------------------------------------

	/** Puts a record back exactly as it was. */
	@Transactional
	public Restored restore(Long itemId, AuthenticatedUser actor) {
		RecycleBinItem item = load(itemId, actor);
		SoftDeletableEntity entity = undelete(item, actor.id());
		if (entity == null) {
			// The row is gone (removed outside the app): nothing to restore.
			items.delete(item);
			throw notFound();
		}
		handler(entity).ifPresent(h -> h.checkRestore(cast(entity)));
		auditor.restored(entityManager, entity, item.getDeletedAt());
		flush("RESTORE_CONFLICT",
				"This item can't come back as it was: another record now uses one of its unique values.");
		items.delete(item);
		return new Restored(item.getEntityType(), item.getEntityId(), item.getLabel());
	}

	/** Deletes a record for good, confirmed with the item's name. */
	@Transactional
	public void purge(Long itemId, String confirm, AuthenticatedUser actor) {
		RecycleBinItem item = load(itemId, actor);
		String expected = confirmationText(item);
		if (confirm == null || !expected.equalsIgnoreCase(confirm.trim())) {
			throw ApiException.badRequest("CONFIRMATION_MISMATCH", "Type \"" + expected + "\" to delete it permanently");
		}
		purgeItem(item);
	}

	/**
	 * Deletes items older than the retention period, oldest first, each in its own transaction so one blocked item
	 * does not stop the rest. Runs as the system. Returns how many were deleted.
	 */
	public int purgeExpired(Function<Long, Boolean> purgeOne) {
		Instant before = clock.instant().minus(Duration.ofDays(settings.recycleBinRetentionDays()));
		int purged = 0;
		Set<Long> failed = new HashSet<>();
		List<Long> batch;
		do {
			batch = items.findIdsDeletedBefore(before, PageRequest.of(0, PURGE_BATCH + failed.size()))
				.stream()
				.filter(id -> !failed.contains(id))
				.toList();
			for (Long id : batch) {
				if (Boolean.TRUE.equals(purgeOne.apply(id))) {
					purged++;
				}
				else {
					failed.add(id);
				}
			}
		}
		while (!batch.isEmpty() && batch.size() >= PURGE_BATCH);
		return purged;
	}

	/** One expired item, in a new transaction (used by {@link #purgeExpired}). False when it could not be deleted. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean purgeExpiredItem(Long itemId) {
		Optional<RecycleBinItem> item = items.findById(itemId);
		if (item.isEmpty()) {
			return true;
		}
		purgeItem(item.get());
		return true;
	}

	private void purgeItem(RecycleBinItem item) {
		SoftDeletableEntity entity = undelete(item, null);
		if (entity != null) {
			handler(entity).ifPresent(h -> h.beforePurge(cast(entity)));
			entityManager.remove(entity);
			flush("PURGE_BLOCKED",
					"Other records still point to this item. Delete those first, then delete this item permanently.");
			handler(entity).ifPresent(h -> h.afterPurge(cast(entity)));
		}
		items.delete(item);
	}

	// --- access -----------------------------------------------------------------------------------------------

	/** The controller already requires the permission; checked again so no other caller skips it. */
	private static void requireBinAccess(AuthenticatedUser viewer) {
		if (!viewer.hasPermission(PermissionCodes.RECYCLE_BIN_MANAGE)) {
			throw ApiException.forbidden("RECYCLE_BIN_FORBIDDEN", "Only a Super Admin can open the recycle bin");
		}
	}

	private RecycleBinItem load(Long itemId, AuthenticatedUser viewer) {
		requireBinAccess(viewer);
		return items.findById(itemId).orElseThrow(RecycleBinService::notFound);
	}

	private Specification<RecycleBinItem> spec(Filter filter) {
		return (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (StringUtils.hasText(filter.module())) {
				where.add(cb.equal(root.get("module"), filter.module().trim()));
			}
			if (StringUtils.hasText(filter.entityType())) {
				where.add(cb.equal(root.get("entityType"), filter.entityType().trim()));
			}
			if (filter.deletedById() != null) {
				where.add(cb.equal(root.get("deletedById"), filter.deletedById()));
			}
			if (filter.from() != null) {
				where.add(cb.greaterThanOrEqualTo(root.<Instant>get("deletedAt"), calendar.startOf(filter.from())));
			}
			if (filter.to() != null) {
				where.add(cb.lessThan(root.<Instant>get("deletedAt"), calendar.startOf(filter.to().plusDays(1))));
			}
			if (StringUtils.hasText(filter.search())) {
				String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
					.replace("%", "\\%")
					.replace("_", "\\_") + "%";
				where.add(cb.like(cb.lower(root.get("label")), pattern, '\\'));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	// --- mapping ----------------------------------------------------------------------------------------------

	private List<Item> toItems(List<RecycleBinItem> page) {
		Set<Long> userIds = page.stream().map(RecycleBinItem::getDeletedById).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, User> users = userIds.isEmpty() ? Map.of()
				: userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
		Set<Long> departmentIds = page.stream().map(RecycleBinItem::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, Department> departments = departmentIds.isEmpty() ? Map.of()
				: departmentRepository.findAllById(departmentIds).stream().collect(Collectors.toMap(Department::getId, Function.identity()));
		int retention = settings.recycleBinRetentionDays();
		List<Item> result = new ArrayList<>(page.size());
		for (RecycleBinItem item : page) {
			Department department = item.getDepartmentId() == null ? null : departments.get(item.getDepartmentId());
			AuditCatalog.Module module = parseModule(item.getModule());
			result.add(new Item(item.getId(), item.getEntityType(), RecordTypes.labelOf(item.getEntityType()),
					item.getEntityId(), item.getLabel(), item.getModule(), module == null ? item.getModule() : module.label(),
					department == null ? null : new DepartmentRef(department.getId(), department.getName()),
					item.getDeletedAt(), UserSummary.of(users.get(item.getDeletedById())),
					item.getDeletedAt().atZone(calendar.zone()).toLocalDate().plusDays(retention), confirmationText(item)));
		}
		return result;
	}

	private static AuditCatalog.Module parseModule(String module) {
		try {
			return AuditCatalog.Module.valueOf(module);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** The item's name, or "Marketing target #12" when it has none. */
	static String confirmationText(RecycleBinItem item) {
		return StringUtils.hasText(item.getLabel()) ? item.getLabel().trim()
				: RecordTypes.labelOf(item.getEntityType()) + " #" + item.getEntityId();
	}

	// --- records ----------------------------------------------------------------------------------------------

	/**
	 * Takes the row out of the bin in SQL, inside the caller's transaction (Hibernate cannot see a deleted row), then
	 * loads it as an ordinary entity. A failed restore or purge rolls this back. Null when the row no longer exists.
	 */
	private SoftDeletableEntity undelete(RecycleBinItem item, Long actorId) {
		BinType type = typeOf(item.getEntityType());
		int rows = entityManager
			.createNativeQuery("UPDATE " + type.table()
					+ " SET deleted_at = NULL, deleted_by = NULL, updated_by = ? WHERE id = ? AND deleted_at IS NOT NULL")
			.setParameter(1, actorId)
			.setParameter(2, item.getEntityId())
			.executeUpdate();
		return rows == 0 ? null : entityManager.find(type.entityClass(), item.getEntityId());
	}

	private BinType typeOf(String type) {
		BinType found = types.get(type);
		if (found == null) {
			throw new IllegalStateException("Not a recycle bin type: " + type);
		}
		return found;
	}

	private void flush(String code, String message) {
		try {
			entityManager.flush();
		}
		catch (DataIntegrityViolationException | PersistenceException ex) {
			log.info("Recycle bin {}: {}", code, ex.getMessage());
			throw ApiException.conflict(code, message);
		}
	}

	private Optional<RecycleBinHandler<?>> handler(SoftDeletableEntity entity) {
		return Optional.ofNullable(handlers().get(entityClass(entity)));
	}

	private Map<Class<?>, RecycleBinHandler<?>> handlers() {
		Map<Class<?>, RecycleBinHandler<?>> resolved = handlers;
		if (resolved == null) {
			resolved = handlerBeans.orderedStream()
				.collect(Collectors.toUnmodifiableMap(RecycleBinHandler::type, h -> h));
			handlers = resolved;
		}
		return resolved;
	}

	@SuppressWarnings("unchecked")
	private static <T extends SoftDeletableEntity> T cast(SoftDeletableEntity entity) {
		return (T) entity;
	}

	private static ApiException notFound() {
		return ApiException.notFound("RECYCLE_BIN_ITEM_NOT_FOUND", "This item is no longer in the recycle bin");
	}

	private static Class<?> entityClass(Object entity) {
		return Hibernate.getClass(entity);
	}

	/** A display name from the record's code, title, name and similar properties. */
	@SuppressWarnings("unchecked")
	private String label(SoftDeletableEntity entity) {
		RecycleBinHandler<SoftDeletableEntity> handler = (RecycleBinHandler<SoftDeletableEntity>) handlers().get(entityClass(entity));
		String fromHandler = handler == null ? null : handler.label(entity);
		if (StringUtils.hasText(fromHandler)) {
			return truncate(fromHandler);
		}
		for (String property : LABEL_PROPERTIES) {
			Object value = read(entity, property);
			if (value instanceof String text && !text.isBlank()) {
				return truncate(text.trim());
			}
		}
		return null;
	}

	/** The handler's answer, else the record's department, else its owner's; null: Super Admins only. */
	@SuppressWarnings("unchecked")
	private Long department(SoftDeletableEntity entity) {
		RecycleBinHandler<SoftDeletableEntity> handler = (RecycleBinHandler<SoftDeletableEntity>) handlers().get(entityClass(entity));
		Long fromHandler = handler == null ? null : handler.departmentOf(entity);
		return fromHandler != null ? fromHandler : departmentOf(entity);
	}

	private static Long departmentOf(SoftDeletableEntity entity) {
		if (read(entity, "department") instanceof Department department) {
			return department.getId();
		}
		if (read(entity, "owner") instanceof User owner && owner.getDepartment() != null) {
			return owner.getDepartment().getId();
		}
		return null;
	}

	private static Object read(Object target, String property) {
		String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
		try {
			Method method = target.getClass().getMethod(getter);
			return method.invoke(target);
		}
		catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
			return null;
		}
	}

	private static String truncate(String text) {
		return text.length() <= 255 ? text : text.substring(0, 254) + "…";
	}

	/** For tests: every record type that goes to the bin. */
	Map<String, String> tables() {
		Map<String, String> tables = new HashMap<>();
		types.values().forEach(t -> tables.put(t.type(), t.table()));
		return tables;
	}

}
