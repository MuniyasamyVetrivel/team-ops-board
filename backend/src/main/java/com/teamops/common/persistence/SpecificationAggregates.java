package com.teamops.common.persistence;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

/**
 * Aggregates over the rows a {@link Specification} matches, so a summary uses the same filters as its list in one
 * grouped query instead of loading entities.
 */
@Component
@Transactional(readOnly = true)
public class SpecificationAggregates {

	@PersistenceContext
	private EntityManager entityManager;

	/** Row counts grouped by {@code attribute}, in no particular order. */
	public <T, K> Map<K, Long> countBy(Class<T> type, Specification<T> spec, String attribute, Class<K> keyType) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<T> root = query.from(type);
		query.multiselect(root.get(attribute), cb.count(root)).where(spec.toPredicate(root, query, cb)).groupBy(root.get(attribute));
		Map<K, Long> counts = new LinkedHashMap<>();
		for (Tuple row : entityManager.createQuery(query).getResultList()) {
			counts.put(row.get(0, keyType), row.get(1, Long.class));
		}
		return counts;
	}

	public <T> long count(Class<T> type, Specification<T> spec) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		Root<T> root = query.from(type);
		query.select(cb.count(root)).where(spec.toPredicate(root, query, cb));
		return entityManager.createQuery(query).getSingleResult();
	}

	/** Sum of a decimal attribute; zero when nothing matches. */
	public <T> BigDecimal sum(Class<T> type, Specification<T> spec, String attribute) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
		Root<T> root = query.from(type);
		query.select(cb.sum(root.<BigDecimal>get(attribute))).where(spec.toPredicate(root, query, cb));
		BigDecimal sum = entityManager.createQuery(query).getSingleResult();
		return sum == null ? BigDecimal.ZERO : sum;
	}

	/** The named attributes of every matching row, as tuples in attribute order. */
	public <T> List<Tuple> select(Class<T> type, Specification<T> spec, String... attributes) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<T> root = query.from(type);
		Selection<?>[] selections = new Selection<?>[attributes.length];
		for (int i = 0; i < attributes.length; i++) {
			selections[i] = root.get(attributes[i]);
		}
		query.multiselect(selections).where(spec.toPredicate(root, query, cb));
		return entityManager.createQuery(query).getResultList();
	}

}
