package com.teamops.marketing.seo.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.RankingChange.Movement;
import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordStatus;

import lombok.RequiredArgsConstructor;

/**
 * The SEO ranking table (brief section 27): keywords filtered and sorted by their standing in a month. Filters and
 * sorts depend on the month's history rows, so this is SQL that returns one page of keyword ids; the caller loads the
 * rows for those ids in batches.
 */
@Repository
@RequiredArgsConstructor
public class SeoRankingTableQuery {

	/** The month's row as {@code cur}, the month before as {@code prev}. */
	private static final String FROM = """
			from marketing_keywords k
			join marketing_pages p on p.id = k.page_id
			left join keyword_ranking_history cur
			  on cur.keyword_id = k.id and cur.ranking_year = :year and cur.ranking_month = :month
			left join keyword_ranking_history prev
			  on prev.keyword_id = k.id and prev.ranking_year = :previousYear and prev.ranking_month = :previousMonth
			""";

	/** Numeric change = previous − current; {@code null} when either month has no position. */
	private static final String CHANGE = "(prev.ranking_position - cur.ranking_position)";

	/**
	 * Public sort keys. "best" puts the highest positions first, then recorded Not Ranked, then months without a
	 * row; "worst" reverses that. Improvement and decline order by the numeric change, empty changes last.
	 */
	static final Map<String, String> SORTS = Map.of(
			"best", "(cur.id is null), (cur.ranking_position is null), cur.ranking_position, k.keyword, k.id",
			"worst", "(cur.id is null), (cur.ranking_position is not null), cur.ranking_position desc, k.keyword, k.id",
			"improvement", "(" + CHANGE + " is null), " + CHANGE + " desc, cur.ranking_position, k.keyword, k.id",
			"decline", "(" + CHANGE + " is null), " + CHANGE + ", cur.ranking_position, k.keyword, k.id",
			"keyword", "k.keyword %s, k.id",
			"page", "p.title %s, k.keyword, k.id",
			"volume", "(k.search_volume is null), k.search_volume %s, k.keyword, k.id",
			"updated", "coalesce(cur.updated_at, k.updated_at) %s, k.id");

	/** Sorts that take a direction; the others have a fixed meaning. */
	private static final Set<String> DIRECTIONAL = Set.of("keyword", "page", "volume", "updated");

	private final NamedParameterJdbcTemplate jdbc;

	/** Status filter on the month's standing; NOT_RANKED includes months without a row (position unavailable). */
	public enum StandingFilter {

		TOP_10, RANKING, NOT_RANKED, NOT_RECORDED

	}

	public record Filter(String search, Long pageId, Long ownerId, Set<KeywordStatus> statuses, Device device,
			StandingFilter standing, Integer minPosition, Integer maxPosition, Movement movement) {
	}

	public record PageOfIds(List<Long> ids, long total) {
	}

	public PageOfIds search(Filter filter, MarketingPeriod period, String sort, int page, int size) {
		MapSqlParameterSource params = params(period);
		String where = where(filter, params);
		long total = jdbc.queryForObject("select count(*) " + FROM + where, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page * size);
		List<Long> ids = jdbc.queryForList(
				"select k.id " + FROM + where + " order by " + orderBy(sort) + " limit :limit offset :offset", params,
				Long.class);
		return new PageOfIds(ids, total);
	}

	/** Every matching id in table order, up to {@code max} (for the CSV export). */
	public List<Long> all(Filter filter, MarketingPeriod period, String sort, int max) {
		return search(filter, period, sort, 0, max).ids();
	}

	/**
	 * Positions per keyword and month for a page's keywords that are not archived, between two months inclusive (the
	 * page history chart).
	 */
	public List<HistoryPoint> pageHistory(Long pageId, MarketingPeriod from, MarketingPeriod to) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("pageId", pageId)
			.addValue("from", from.year() * 12 + from.month())
			.addValue("to", to.year() * 12 + to.month());
		List<HistoryPoint> points = new ArrayList<>();
		jdbc.query("""
				select h.keyword_id, h.ranking_year, h.ranking_month, h.ranking_position
				from keyword_ranking_history h
				join marketing_keywords k on k.id = h.keyword_id
				where k.page_id = :pageId and k.status <> 'ARCHIVED'
				  and h.ranking_year * 12 + h.ranking_month between :from and :to
				""", params, rs -> {
			points.add(new HistoryPoint(rs.getLong("keyword_id"),
					new MarketingPeriod(rs.getInt("ranking_month"), rs.getInt("ranking_year")),
					rs.getObject("ranking_position", Integer.class)));
		});
		return points;
	}

	/** One recorded month; {@code position} is {@code null} for Not Ranked. */
	public record HistoryPoint(Long keywordId, MarketingPeriod period, Integer position) {
	}

	private static MapSqlParameterSource params(MarketingPeriod period) {
		MarketingPeriod previous = period.previous();
		return new MapSqlParameterSource().addValue("year", period.year())
			.addValue("month", period.month())
			.addValue("previousYear", previous.year())
			.addValue("previousMonth", previous.month());
	}

	private static String where(Filter filter, MapSqlParameterSource params) {
		StringBuilder where = new StringBuilder("where 1 = 1");
		if (StringUtils.hasText(filter.search())) {
			where.append(" and lower(k.keyword) like :search escape '!'");
			params.addValue("search", "%" + filter.search()
				.trim()
				.toLowerCase(Locale.ROOT)
				.replace("!", "!!")
				.replace("%", "!%")
				.replace("_", "!_") + "%");
		}
		if (filter.pageId() != null) {
			where.append(" and k.page_id = :pageId");
			params.addValue("pageId", filter.pageId());
		}
		if (filter.ownerId() != null) {
			where.append(" and k.owner_id = :ownerId");
			params.addValue("ownerId", filter.ownerId());
		}
		if (filter.statuses() != null && !filter.statuses().isEmpty()) {
			where.append(" and k.status in (:statuses)");
			params.addValue("statuses", filter.statuses().stream().map(Enum::name).toList());
		}
		if (filter.device() != null) {
			where.append(" and k.device = :device");
			params.addValue("device", filter.device().name());
		}
		if (filter.standing() != null) {
			where.append(switch (filter.standing()) {
				case TOP_10 -> " and cur.ranking_position between 1 and 10";
				case RANKING -> " and cur.ranking_position between 11 and 100";
				case NOT_RANKED -> " and cur.ranking_position is null";
				case NOT_RECORDED -> " and cur.id is null";
			});
		}
		if (filter.minPosition() != null) {
			where.append(" and cur.ranking_position >= :minPosition");
			params.addValue("minPosition", filter.minPosition());
		}
		if (filter.maxPosition() != null) {
			where.append(" and cur.ranking_position <= :maxPosition");
			params.addValue("maxPosition", filter.maxPosition());
		}
		if (filter.movement() != null) {
			// Mirrors RankingChange: entering the rankings is an improvement, dropping out a decline.
			where.append(switch (filter.movement()) {
				case IMPROVED -> " and cur.id is not null and prev.id is not null and cur.ranking_position is not null"
						+ " and (prev.ranking_position is null or prev.ranking_position > cur.ranking_position)";
				case DECLINED -> " and cur.id is not null and prev.id is not null and prev.ranking_position is not null"
						+ " and (cur.ranking_position is null or cur.ranking_position > prev.ranking_position)";
				case UNCHANGED -> " and cur.id is not null and prev.id is not null"
						+ " and cur.ranking_position <=> prev.ranking_position";
				case NEW -> " and cur.id is not null and prev.id is null";
			});
		}
		return where.toString();
	}

	static String orderBy(String sort) {
		String[] parts = (StringUtils.hasText(sort) ? sort : "best").split(",");
		String key = parts[0].trim();
		String template = SORTS.get(key);
		if (template == null) {
			throw ApiException.badRequest("INVALID_SORT",
					"Unsupported sort field '" + key + "'. Allowed: " + SORTS.keySet());
		}
		String direction = "asc";
		if (parts.length > 1) {
			direction = parts[1].trim().toLowerCase(Locale.ROOT);
			if (!direction.equals("asc") && !direction.equals("desc")) {
				throw ApiException.badRequest("INVALID_SORT", "Sort direction must be asc or desc");
			}
		}
		return DIRECTIONAL.contains(key) ? template.formatted(direction) : template;
	}

}
