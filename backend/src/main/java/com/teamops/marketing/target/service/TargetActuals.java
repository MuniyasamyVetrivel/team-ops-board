package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

/** The registered {@link TargetActualSource}s: which target types compute their actual, and the computed values. */
@Component
public class TargetActuals {

	private final Map<ActualSource, TargetActualSource> sources = new EnumMap<>(ActualSource.class);

	public TargetActuals(List<TargetActualSource> sources) {
		sources.forEach(source -> this.sources.put(source.source(), source));
	}

	/** Whether the type's actual is computed now (its source is not MANUAL and its module exists). */
	public boolean isAutomatic(TargetType type) {
		return type.getActualSource() != ActualSource.MANUAL && sources.containsKey(type.getActualSource());
	}

	/** Computed actuals per month for an automatic type; empty for any other type. */
	public Map<MarketingPeriod, BigDecimal> computed(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		return isAutomatic(type) ? sources.get(type.getActualSource()).actuals(type, from, to) : Map.of();
	}

	/**
	 * The month's computed actual of every automatic type among {@code types}, by type id: one call per actual
	 * source, so types that share a source share its query. Types without a computed value are missing.
	 */
	public Map<Long, BigDecimal> computedFor(Collection<TargetType> types, MarketingPeriod period) {
		Map<ActualSource, List<TargetType>> bySource = new EnumMap<>(ActualSource.class);
		types.stream()
			.filter(this::isAutomatic)
			.distinct()
			.forEach(type -> bySource.computeIfAbsent(type.getActualSource(), s -> new ArrayList<>()).add(type));
		Map<Long, BigDecimal> computed = new HashMap<>();
		bySource.forEach((source, group) -> sources.get(source).actualsFor(group, period, period).forEach((typeId, months) -> {
			BigDecimal value = months.get(period);
			if (value != null) {
				computed.put(typeId, value);
			}
		}));
		return computed;
	}

}
