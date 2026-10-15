package com.teamops.common.audit;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.teamops.common.audit.AuditCatalog.Module;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;

/** Every audited record type the application maps, with its label and module (for the viewer's filters). */
@Component
public class RecordTypeCatalog {

	public record RecordType(String type, String label, Module module) {

	}

	private final List<RecordType> types;

	private final Map<String, RecordType> byType;

	public RecordTypeCatalog(EntityManagerFactory entityManagerFactory) {
		Map<String, RecordType> found = new LinkedHashMap<>();
		entityManagerFactory.getMetamodel()
			.getEntities()
			.stream()
			.map(EntityType::getJavaType)
			.filter(RecordTypes::isAudited)
			.forEach(type -> {
				String name = RecordTypes.typeOf(type);
				found.putIfAbsent(name, new RecordType(name, RecordTypes.labelOf(name), RecordTypes.moduleOf(type)));
			});
		this.types = found.values().stream().sorted(Comparator.comparing(RecordType::label)).toList();
		this.byType = Map.copyOf(found);
	}

	public List<RecordType> all() {
		return types;
	}

	/** The module of a record type, or null for a type the application does not map. */
	public Module moduleOf(String type) {
		RecordType found = type == null ? null : byType.get(type);
		return found == null ? null : found.module();
	}

	public Set<String> typesIn(Module module) {
		return types.stream()
			.filter(type -> type.module() == module)
			.map(RecordType::type)
			.collect(Collectors.toUnmodifiableSet());
	}

}
