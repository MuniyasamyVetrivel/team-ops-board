package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/**
 * Landing Pages Created: SEO pages of type LANDING_PAGE created in the month, with months taken in the business time
 * zone. Every month in the range has a value (zero is a measured result here).
 */
@Component
@RequiredArgsConstructor
class LandingPagesActualSource implements TargetActualSource {

	private final NamedParameterJdbcTemplate jdbc;

	private final BusinessCalendar calendar;

	@Override
	public ActualSource source() {
		return ActualSource.LANDING_PAGES;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		ZoneId zone = calendar.zone();
		Map<MarketingPeriod, BigDecimal> actuals = new HashMap<>();
		for (MarketingPeriod p = from; !p.firstDay().isAfter(to.firstDay()); p = p.next()) {
			actuals.put(p, BigDecimal.ZERO);
		}
		// DATETIME columns hold UTC; the month boundaries are business-zone midnights.
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("start", utc(calendar.startOf(from.firstDay())))
			.addValue("end", utc(calendar.startOf(to.next().firstDay())));
		jdbc.query("""
				select created_at from marketing_pages
				where page_type = 'LANDING_PAGE' and created_at >= :start and created_at < :end
				""", params, rs -> {
			LocalDateTime created = rs.getObject("created_at", LocalDateTime.class);
			MarketingPeriod period = MarketingPeriod.of(created.toInstant(ZoneOffset.UTC).atZone(zone).toLocalDate());
			actuals.merge(period, BigDecimal.ONE, BigDecimal::add);
		});
		return actuals;
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
