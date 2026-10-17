package com.teamops.backup.service;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.springframework.stereotype.Component;

import com.teamops.common.audit.AuditCatalog;
import com.teamops.common.audit.AuditFinance;
import com.teamops.common.audit.RecordTypes;
import com.teamops.common.storage.StoredFile;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;

/**
 * What "Export all data" leaves out for an exporter without FINANCE_VIEW (brief X8: finance data is hidden everywhere
 * from them), worked out from the entity model so new finance fields are covered without a list to keep:
 * <ul>
 * <li>the Finance Tracker's tables (entities in the {@code finance} package), and the stored files they point at
 * (receipts), in {@code uploads/} and in the {@code files} table;</li>
 * <li>money columns elsewhere (fields marked {@link AuditFinance}, such as approval amounts and contract values);</li>
 * <li>in rows about a finance record ({@code entity_type} of a finance record type: audit entries, notifications,
 * recycle bin items), their names, titles and details; and the details of audit entries flagged as holding finance
 * amounts.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class FinanceRedaction {

	/** What a redacted cell reads. */
	static final String HIDDEN = "[finance]";

	/** Columns of rows about a finance record that carry its name, title or details. */
	static final Set<String> RECORD_TEXT_COLUMNS = Set.of("details", "entity_label", "label", "title", "body");

	private final EntityManagerFactory entityManagerFactory;

	private volatile Model model;

	/**
	 * @param tables finance tables, left out entirely
	 * @param columns money columns of other tables, by table
	 * @param recordTypes finance record types, as {@code entity_type} columns name them
	 * @param fileColumns {table, column} pairs of finance tables that point at stored files
	 */
	record Model(Set<String> tables, Map<String, Set<String>> columns, Set<String> recordTypes,
			Set<String[]> fileColumns) {

	}

	Model model() {
		Model current = model;
		if (current == null) {
			current = build();
			model = current;
		}
		return current;
	}

	/** The ids and storage keys of stored files finance records point at, read on the export's connection. */
	record Files(Set<Long> ids, Set<String> storageKeys) {

	}

	Files financeFiles(Connection connection) throws SQLException {
		Set<Long> ids = new HashSet<>();
		Set<String> keys = new HashSet<>();
		for (String[] pair : model().fileColumns()) {
			String sql = "SELECT f.id, f.storage_key FROM files f JOIN `" + pair[0] + "` r ON r.`" + pair[1] + "` = f.id";
			try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rs = query.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getLong(1));
					keys.add(rs.getString(2));
				}
			}
		}
		return new Files(ids, keys);
	}

	private Model build() {
		Set<String> tables = new HashSet<>();
		Map<String, Set<String>> columns = new HashMap<>();
		Set<String> types = new HashSet<>();
		Set<String[]> fileColumns = new HashSet<>();
		SessionFactoryImplementor factory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
		factory.getMappingMetamodel().forEachEntityDescriptor(descriptor -> {
			if (!(descriptor instanceof AbstractEntityPersister persister)) {
				return;
			}
			Class<?> mapped = persister.getMappedClass();
			String table = name(persister.getTableName());
			boolean finance = RecordTypes.moduleOf(mapped) == AuditCatalog.Module.FINANCE;
			if (finance) {
				tables.add(table);
				types.add(RecordTypes.typeOf(mapped));
			}
			for (String property : persister.getPropertyNames()) {
				Field field = field(mapped, property);
				if (field == null) {
					continue;
				}
				if (finance && StoredFile.class.isAssignableFrom(field.getType())) {
					for (String column : persister.getPropertyColumnNames(property)) {
						fileColumns.add(new String[] { table, name(column) });
					}
				}
				if (!finance && field.isAnnotationPresent(AuditFinance.class)) {
					for (String column : persister.getPropertyColumnNames(property)) {
						columns.computeIfAbsent(table, t -> new HashSet<>()).add(name(column));
					}
				}
			}
		});
		return new Model(Set.copyOf(tables), Map.copyOf(columns), Set.copyOf(types), Set.copyOf(fileColumns));
	}

	private static String name(String sqlName) {
		String plain = sqlName.replace("`", "").replace("\"", "");
		int dot = plain.lastIndexOf('.');
		return (dot < 0 ? plain : plain.substring(dot + 1)).toLowerCase(Locale.ROOT);
	}

	private static Field field(Class<?> type, String name) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				return c.getDeclaredField(name);
			}
			catch (NoSuchFieldException ex) {
				// try the superclass
			}
		}
		return null;
	}

}
