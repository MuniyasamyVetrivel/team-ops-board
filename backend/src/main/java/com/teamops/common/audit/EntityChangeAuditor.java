package com.teamops.common.audit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.engine.spi.TransactionCompletionCallbacks.AfterCompletionCallback;
import org.hibernate.engine.spi.TransactionCompletionCallbacks.BeforeCompletionCallback;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractCollectionEvent;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCollectionRecreateEvent;
import org.hibernate.event.spi.PostCollectionRecreateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.event.spi.PreCollectionRemoveEvent;
import org.hibernate.event.spi.PreCollectionRemoveEventListener;
import org.hibernate.event.spi.PreCollectionUpdateEvent;
import org.hibernate.event.spi.PreCollectionUpdateEventListener;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.ComponentType;
import org.hibernate.type.Type;
import org.springframework.stereotype.Component;

import com.teamops.common.web.ClientInfo;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Record history for every entity, in one place: Hibernate reports each insert, update and delete (and each change
 * to a many-to-many or element collection), and this listener turns it into an audit entry with a field-by-field
 * {@code from → to} diff. Entries are collected per transaction ({@link PendingAudit}) and written with plain JDBC
 * just before the transaction commits, on the same connection, so a rolled-back change leaves no entry and a
 * committed one always has its entry.
 * <p>
 * What is left out: entities and fields marked {@link AuditIgnore}; the audit stamps and version column; secrets
 * (named like passwords, tokens or hashes), which are only marked as changed. Money fields marked
 * {@link AuditFinance} are flagged so the viewer can hide them. Bulk JPQL/SQL statements bypass Hibernate's events,
 * so business deletes and updates go through entities.
 * <p>
 * Soft deletes are recognised by a {@code deletedAt} field: setting it logs a delete, clearing it a restore, and
 * removing such a row for good logs a permanent delete.
 */
@Component
@RequiredArgsConstructor
public class EntityChangeAuditor implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener,
		PreCollectionUpdateEventListener, PreCollectionRemoveEventListener, PostCollectionRecreateEventListener {

	private static final String INSERT = """
			INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, entity_label, parent_type, parent_id,
			                        details, finance, ip_address, user_agent, created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

	/** Audit stamps say who and when; the entry itself records that. */
	private static final Set<String> STAMPS = Set.of("createdAt", "createdById", "updatedAt", "updatedById", "deletedById");

	/** The first of these a record has names it in the log ("TSK-000123", a title, a name, an email). */
	private static final List<String> LABEL_FIELDS = List.of("code", "title", "name", "subject", "keyword",
			"email", "originalName", "url");

	private static final int MAX_LABEL = 255;

	private final EntityManagerFactory entityManagerFactory;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	private final Map<Class<?>, EntityModel> models = new ConcurrentHashMap<>();

	private final Map<SharedSessionContractImplementor, PendingAudit> pending = new ConcurrentHashMap<>();

	@PostConstruct
	void register() {
		EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
			.getServiceRegistry()
			.requireService(EventListenerRegistry.class);
		registry.appendListeners(EventType.POST_INSERT, this);
		registry.appendListeners(EventType.POST_UPDATE, this);
		registry.appendListeners(EventType.POST_DELETE, this);
		registry.appendListeners(EventType.PRE_COLLECTION_UPDATE, this);
		registry.appendListeners(EventType.PRE_COLLECTION_REMOVE, this);
		registry.appendListeners(EventType.POST_COLLECTION_RECREATE, this);
	}

	// --- entity events ----------------------------------------------------------------------------------------

	@Override
	public void onPostInsert(PostInsertEvent event) {
		EntityModel model = model(event.getPersister());
		if (model == null) {
			return;
		}
		SharedSessionContractImplementor session = event.getSession();
		Object[] state = event.getState();
		RecordChange change = start(AuditAction.RECORD_CREATED, model, event.getId(), event.getEntity(), state);
		for (Property property : model.properties) {
			if (property.sensitive) {
				continue;
			}
			Object value = property.read(state[property.index], session);
			if (value != null) {
				change.change(property.name, null, value, property.ref, property.finance);
			}
		}
		buffer(session).add(change);
	}

	@Override
	public void onPostUpdate(PostUpdateEvent event) {
		EntityModel model = model(event.getPersister());
		if (model == null) {
			return;
		}
		SharedSessionContractImplementor session = event.getSession();
		Object[] old = event.getOldState();
		Object[] state = event.getState();
		AuditAction action = AuditAction.RECORD_UPDATED;
		if (model.deletedAtIndex >= 0 && old != null) {
			boolean wasDeleted = old[model.deletedAtIndex] != null;
			boolean isDeleted = state[model.deletedAtIndex] != null;
			if (!wasDeleted && isDeleted) {
				action = AuditAction.RECORD_DELETED;
			}
			else if (wasDeleted && !isDeleted) {
				action = AuditAction.RECORD_RESTORED;
			}
		}
		int[] dirty = event.getDirtyProperties();
		RecordChange change = start(action, model, event.getId(), event.getEntity(), state);
		for (Property property : model.properties) {
			if (dirty != null && !contains(dirty, property.index)) {
				continue;
			}
			Object from = old == null ? null : property.read(old[property.index], session);
			Object to = property.read(state[property.index], session);
			if (old != null && AuditValues.same(from, to)) {
				continue;
			}
			if (property.sensitive) {
				change.redacted(property.name);
			}
			else {
				change.change(property.name, from, to, property.ref, property.finance);
			}
		}
		if (action == AuditAction.RECORD_UPDATED && change.changes.isEmpty()) {
			return;
		}
		buffer(session).add(change);
	}

	@Override
	public void onPostDelete(PostDeleteEvent event) {
		EntityModel model = model(event.getPersister());
		if (model == null) {
			return;
		}
		SharedSessionContractImplementor session = event.getSession();
		Object[] state = event.getDeletedState();
		AuditAction action = model.deletedAtIndex >= 0 ? AuditAction.RECORD_PURGED : AuditAction.RECORD_DELETED;
		RecordChange change = start(action, model, event.getId(), event.getEntity(), state);
		for (Property property : model.properties) {
			if (property.sensitive || state == null) {
				continue;
			}
			Object value = property.read(state[property.index], session);
			if (value != null) {
				change.change(property.name, value, null, property.ref, property.finance);
			}
		}
		buffer(session).add(change);
	}

	// --- collection events (many-to-many links and element collections) ----------------------------------------

	@Override
	public void onPreUpdateCollection(PreCollectionUpdateEvent event) {
		collectionChanged(event, snapshot(event.getCollection()), elements(event.getCollection()));
	}

	@Override
	public void onPreRemoveCollection(PreCollectionRemoveEvent event) {
		Object owner = event.getAffectedOwnerOrNull();
		if (owner == null || isBeingDeleted(event.getSession(), owner)) {
			return;
		}
		Collection<?> before = snapshot(event.getCollection());
		if (before != null) {
			collectionChanged(event, before, List.of());
		}
	}

	@Override
	public void onPostRecreateCollection(PostCollectionRecreateEvent event) {
		collectionChanged(event, List.of(), elements(event.getCollection()));
	}

	private void collectionChanged(AbstractCollectionEvent event, Collection<?> before, Collection<?> after) {
		CollectionPersister persister = event.getCollectionPersister();
		Object owner = event.getAffectedOwnerOrNull();
		if (persister == null || persister.isInverse() || persister.isOneToMany() || owner == null || before == null
				|| after == null) {
			return;
		}
		SharedSessionContractImplementor session = event.getSession();
		EntityModel model = model(session.getEntityPersister(null, owner));
		String role = persister.getRole();
		String field = role.substring(role.lastIndexOf('.') + 1);
		if (model == null || model.ignoredFields.contains(field)) {
			return;
		}
		Class<?> elementEntity = persister.getAttributeMapping().getElementDescriptor()
				.getPartMappingType() instanceof EntityMappingType entity
						? entity.getMappedJavaType().getJavaTypeClass()
						: null;
		String ref = elementEntity != null ? RecordTypes.typeOf(elementEntity) : null;
		List<Object> from = elementValues(before, elementEntity != null, session);
		List<Object> to = elementValues(after, elementEntity != null, session);
		if (AuditValues.same(from, to)) {
			return;
		}
		RecordChange change = start(AuditAction.RECORD_UPDATED, model, event.getAffectedOwnerIdOrNull(), owner,
				null);
		change.change(field, from, to, ref, false);
		buffer(session).add(change);
	}

	private List<Object> elementValues(Collection<?> elements, boolean entities, SharedSessionContractImplementor session) {
		List<Object> values = new ArrayList<>(elements.size());
		for (Object element : elements) {
			values.add(entities ? idOf(element, session) : AuditValues.normalize(element));
		}
		values.sort(Comparator.comparing(String::valueOf));
		return values;
	}

	private static Collection<?> snapshot(PersistentCollection<?> collection) {
		Object snapshot = collection.getStoredSnapshot();
		if (snapshot instanceof Map<?, ?> map) {
			return map.values();
		}
		return snapshot instanceof Collection<?> list ? list : null;
	}

	private static Collection<?> elements(PersistentCollection<?> collection) {
		if (collection instanceof Map<?, ?> map) {
			return map.values();
		}
		return collection instanceof Collection<?> list ? list : null;
	}

	private static boolean isBeingDeleted(SharedSessionContractImplementor session, Object entity) {
		EntityEntry entry = session.getPersistenceContext().getEntry(entity);
		return entry != null && (entry.getStatus() == Status.DELETED || entry.getStatus() == Status.GONE);
	}

	/**
	 * Logs that a record came back from the recycle bin. The restore itself is an SQL update (Hibernate cannot see a
	 * deleted row), so it raises no event; this adds the entry to the current transaction's history instead.
	 */
	public void restored(EntityManager entityManager, Object entity, Instant deletedAt) {
		SharedSessionContractImplementor session = entityManager.unwrap(SharedSessionContractImplementor.class);
		EntityPersister persister = session.getEntityPersister(null, entity);
		EntityModel model = model(persister);
		if (model == null) {
			return;
		}
		RecordChange change = start(AuditAction.RECORD_RESTORED, model, persister.getIdentifier(entity, session),
				entity, persister.getValues(entity));
		change.change("deletedAt", deletedAt == null ? null : deletedAt.toString(), null, null, false);
		buffer(session).add(change);
	}

	// --- writing ----------------------------------------------------------------------------------------------

	private PendingAudit buffer(SharedSessionContractImplementor session) {
		return pending.computeIfAbsent(session, s -> {
			s.getTransactionCompletionCallbacks().registerCallback((BeforeCompletionCallback) this::write);
			s.getTransactionCompletionCallbacks()
				.registerCallback((AfterCompletionCallback) (success, done) -> pending.remove(done));
			return new PendingAudit();
		});
	}

	private void write(SharedSessionContractImplementor session) {
		PendingAudit audit = pending.remove(session);
		List<RecordChange> entries = audit == null ? List.of() : audit.drain();
		if (entries.isEmpty()) {
			return;
		}
		LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		session.doWork(connection -> {
			try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
				for (RecordChange entry : entries) {
					insert.setObject(1, entry.actorId, Types.BIGINT);
					insert.setString(2, entry.action.name());
					insert.setString(3, entry.entityType);
					insert.setObject(4, entry.entityId, Types.BIGINT);
					insert.setString(5, entry.label);
					insert.setString(6, entry.parent == null ? null : entry.parent.type());
					insert.setObject(7, entry.parent == null ? null : entry.parent.id(), Types.BIGINT);
					insert.setString(8, objectMapper.writeValueAsString(entry.details()));
					insert.setBoolean(9, entry.finance());
					insert.setString(10, entry.ipAddress);
					insert.setString(11, entry.userAgent);
					insert.setObject(12, now);
					insert.addBatch();
				}
				insert.executeBatch();
			}
		});
	}

	// --- model ------------------------------------------------------------------------------------------------

	private RecordChange start(AuditAction action, EntityModel model, Object id, Object entity, Object[] state) {
		ClientInfo client = AuditActor.currentClient();
		RecordChange change = new RecordChange(action, model.type, id instanceof Long longId ? longId : null,
				id instanceof Long || id == null ? null : keyOf(id), AuditActor.currentUserId(),
				client == null ? null : client.ipAddress(), client == null ? null : client.userAgent());
		change.label = state == null ? null : model.label(state);
		if (entity instanceof AuditedChild child) {
			change.parent = child.auditParent();
		}
		return change;
	}

	private EntityModel model(EntityPersister persister) {
		Class<?> type = persister.getMappedClass();
		if (!RecordTypes.isAudited(type)) {
			return null;
		}
		return models.computeIfAbsent(type, t -> EntityModel.of(persister, this));
	}

	private Object idOf(Object entity, SharedSessionContractImplementor session) {
		if (entity == null) {
			return null;
		}
		Object id = session.getContextEntityIdentifier(entity);
		return id != null ? id : entityManagerFactory.getPersistenceUnitUtil().getIdentifier(entity);
	}

	/** An embedded id as field → value (attachment rows keyed by parent and file). */
	private static Map<String, Object> keyOf(Object id) {
		Map<String, Object> key = new LinkedHashMap<>();
		for (Class<?> c = id.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || !field.trySetAccessible()) {
					continue;
				}
				try {
					key.put(field.getName(), AuditValues.normalize(field.get(id)));
				}
				catch (IllegalAccessException ex) {
					// not readable: leave it out of the key
				}
			}
		}
		return key.isEmpty() ? Map.of("id", String.valueOf(id)) : key;
	}

	private static boolean contains(int[] values, int value) {
		for (int candidate : values) {
			if (candidate == value) {
				return true;
			}
		}
		return false;
	}

	private static Field field(Class<?> type, String name) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				return c.getDeclaredField(name);
			}
			catch (NoSuchFieldException ex) {
				// look further up
			}
		}
		return null;
	}

	/** How one mapped property is read into the diff. */
	private record Property(int index, String name, Kind kind, String ref, boolean sensitive, boolean finance,
			Type type, EntityChangeAuditor auditor) {

		enum Kind {

			VALUE, REFERENCE, EMBEDDED

		}

		Object read(Object value, SharedSessionContractImplementor session) {
			if (value == null) {
				return null;
			}
			return switch (kind) {
				case REFERENCE -> auditor.idOf(value, session);
				case EMBEDDED -> embedded((ComponentType) type, value, session);
				case VALUE -> AuditValues.normalize(value);
			};
		}

		private Object embedded(ComponentType component, Object value, SharedSessionContractImplementor session) {
			String[] names = component.getPropertyNames();
			Type[] types = component.getSubtypes();
			Object[] values = component.getPropertyValues(value);
			Map<String, Object> result = new LinkedHashMap<>();
			for (int i = 0; i < names.length; i++) {
				Object part = values[i];
				result.put(names[i], part == null ? null
						: types[i].isEntityType() ? auditor.idOf(part, session) : AuditValues.normalize(part));
			}
			return result;
		}

	}

	/** What the auditor needs to know about an entity class, worked out once. */
	private record EntityModel(String type, List<Property> properties, int firstNameIndex, int lastNameIndex,
			int[] labelIndexes, int deletedAtIndex, Set<String> ignoredFields) {

		static EntityModel of(EntityPersister persister, EntityChangeAuditor auditor) {
			Class<?> mapped = persister.getMappedClass();
			String[] names = persister.getPropertyNames();
			Type[] types = persister.getPropertyTypes();
			int version = persister.isVersioned() ? persister.getVersionPropertyIndex() : -1;
			List<Property> properties = new ArrayList<>();
			Set<String> ignored = new HashSet<>();
			int deletedAt = -1;
			for (int i = 0; i < names.length; i++) {
				String name = names[i];
				Field field = field(mapped, name);
				if (field != null && field.isAnnotationPresent(AuditIgnore.class)) {
					ignored.add(name);
					continue;
				}
				if ("deletedAt".equals(name)) {
					deletedAt = i;
				}
				if (i == version || STAMPS.contains(name) || types[i].isCollectionType()) {
					continue;
				}
				Property.Kind kind = types[i].isEntityType() ? Property.Kind.REFERENCE
						: types[i].isComponentType() ? Property.Kind.EMBEDDED : Property.Kind.VALUE;
				String ref = kind == Property.Kind.REFERENCE ? RecordTypes.typeOf(types[i].getReturnedClass()) : null;
				properties.add(new Property(i, name, kind, ref, AuditValues.isSensitive(name),
						field != null && field.isAnnotationPresent(AuditFinance.class), types[i], auditor));
			}
			List<String> nameList = List.of(names);
			int[] label = LABEL_FIELDS.stream().filter(nameList::contains).mapToInt(nameList::indexOf).toArray();
			return new EntityModel(RecordTypes.typeOf(mapped), List.copyOf(properties), nameList.indexOf("firstName"),
					nameList.indexOf("lastName"), label, deletedAt, Set.copyOf(ignored));
		}

		/** The record's display name: a person's full name, otherwise the first label field with a value. */
		String label(Object[] state) {
			if (firstNameIndex >= 0 && state[firstNameIndex] instanceof String first) {
				String last = lastNameIndex >= 0 ? Objects.toString(state[lastNameIndex], "") : "";
				String full = (first + " " + last).trim();
				if (!full.isEmpty()) {
					return truncate(full);
				}
			}
			for (int index : labelIndexes) {
				if (state[index] instanceof CharSequence text && !text.toString().isBlank()) {
					return truncate(text.toString());
				}
			}
			return null;
		}

		private static String truncate(String label) {
			return label.length() <= MAX_LABEL ? label : label.substring(0, MAX_LABEL - 1) + "…";
		}

	}

}
