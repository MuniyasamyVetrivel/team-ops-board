package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.repository.ContentQuery;
import com.teamops.marketing.content.repository.ContentQuery.MonthFigures;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;

import lombok.RequiredArgsConstructor;

/** Blogs Published: live blog posts (PUBLISHED or UPDATED) with a publication date in the month. Zero is a result. */
@Component
@RequiredArgsConstructor
class BlogsPublishedActualSource implements TargetActualSource {

	private final ContentQuery contentQuery;

	@Override
	public ActualSource source() {
		return ActualSource.BLOGS_PUBLISHED;
	}

	@Override
	public Map<MarketingPeriod, BigDecimal> actuals(TargetType type, MarketingPeriod from, MarketingPeriod to) {
		Map<MarketingPeriod, MonthFigures> figures = contentQuery.monthly(from, to, null);
		Map<MarketingPeriod, BigDecimal> actuals = new HashMap<>();
		for (MarketingPeriod p = from; !p.firstDay().isAfter(to.firstDay()); p = p.next()) {
			actuals.put(p, BigDecimal.valueOf(figures.getOrDefault(p, MonthFigures.ZERO).publishedBlogs()));
		}
		return actuals;
	}

}
