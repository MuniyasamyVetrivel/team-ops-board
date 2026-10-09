package com.teamops.marketing.backlink.repository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.teamops.marketing.backlink.entity.BacklinkStatus;
import com.teamops.marketing.backlink.entity.BacklinkType;
import com.teamops.marketing.common.MarketingPeriod;

import lombok.RequiredArgsConstructor;

/**
 * Backlink counts from grouped queries. Every stage counts in the month of its own date, so one backlink submitted in
 * September and live in October counts as submitted in September and live in October.
 */
@Repository
@RequiredArgsConstructor
public class BacklinkQuery {

	/** The dated stages, as counted by month. */
	public enum StageKind {

		SUBMITTED("submitted_date"), APPROVED("approved_date"), LIVE("live_date"), REJECTED("rejected_date"),
		LOST("lost_date");

		final String column;

		StageKind(String column) {
			this.column = column;
		}

	}

	/** One month's stage counts. */
	public record StageCounts(long submitted, long approved, long live, long rejected, long lost) {

		public static final StageCounts ZERO = new StageCounts(0, 0, 0, 0, 0);

		StageCounts plus(StageKind kind, long count) {
			return switch (kind) {
				case SUBMITTED -> new StageCounts(submitted + count, approved, live, rejected, lost);
				case APPROVED -> new StageCounts(submitted, approved + count, live, rejected, lost);
				case LIVE -> new StageCounts(submitted, approved, live + count, rejected, lost);
				case REJECTED -> new StageCounts(submitted, approved, live, rejected + count, lost);
				case LOST -> new StageCounts(submitted, approved, live, rejected, lost + count);
			};
		}

		StageCounts plus(StageCounts other) {
			return new StageCounts(submitted + other.submitted, approved + other.approved, live + other.live,
					rejected + other.rejected, lost + other.lost);
		}

	}

	/** One owner's stage counts in a month ({@code ownerId} null for backlinks without an owner). */
	public record OwnerCounts(Long ownerId, String ownerName, StageCounts counts) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	/** Stage counts per month from {@code from} to {@code to}; months without activity are missing. */
	public Map<MarketingPeriod, StageCounts> monthly(MarketingPeriod from, MarketingPeriod to, Long ownerId) {
		Map<MarketingPeriod, StageCounts> counts = new HashMap<>();
		String sql = union("year(b.%1$s) as y, month(b.%1$s) as m", "year(b.%1$s), month(b.%1$s)", "", owner(ownerId));
		jdbc.query(sql, params(from, to, ownerId), rs -> {
			MarketingPeriod period = new MarketingPeriod(rs.getInt("m"), rs.getInt("y"));
			counts.merge(period, StageCounts.ZERO.plus(StageKind.valueOf(rs.getString("kind")), rs.getLong("n")),
					StageCounts::plus);
		});
		return counts;
	}

	/** Links that went live in the month, per link type. */
	public Map<BacklinkType, Long> liveByType(MarketingPeriod period, Long ownerId) {
		Map<BacklinkType, Long> counts = new EnumMap<>(BacklinkType.class);
		jdbc.query("select b.link_type, count(*) as n from backlinks b where b.live_date between :from and :to"
				+ owner(ownerId) + " group by b.link_type", params(period, period, ownerId), rs -> {
					counts.put(BacklinkType.valueOf(rs.getString("link_type")), rs.getLong("n"));
				});
		return counts;
	}

	/** Each owner's stage counts in the month, most live links first. */
	public List<OwnerCounts> byOwner(MarketingPeriod period) {
		Map<Long, StageCounts> counts = new LinkedHashMap<>();
		Map<Long, String> names = new HashMap<>();
		String sql = union("b.owner_id as owner_id, u.first_name as first_name, u.last_name as last_name",
				"b.owner_id, u.first_name, u.last_name", " left join users u on u.id = b.owner_id", "");
		jdbc.query(sql, params(period, period, null), rs -> {
			long id = rs.getLong("owner_id");
			Long ownerId = rs.wasNull() ? null : id;
			if (ownerId != null) {
				names.put(ownerId, (rs.getString("first_name") + " " + rs.getString("last_name")).trim());
			}
			counts.merge(ownerId, StageCounts.ZERO.plus(StageKind.valueOf(rs.getString("kind")), rs.getLong("n")),
					StageCounts::plus);
		});
		List<OwnerCounts> owners = new ArrayList<>();
		counts.forEach((id, c) -> owners.add(new OwnerCounts(id, names.get(id), c)));
		owners.sort((a, b) -> a.counts().live() != b.counts().live() ? Long.compare(b.counts().live(), a.counts().live())
				: Long.compare(b.counts().submitted(), a.counts().submitted()));
		return owners;
	}

	/** Backlinks per current status (the pipeline today), optionally for one owner. */
	public Map<BacklinkStatus, Long> byStatus(Long ownerId) {
		Map<BacklinkStatus, Long> counts = new EnumMap<>(BacklinkStatus.class);
		MapSqlParameterSource params = new MapSqlParameterSource();
		if (ownerId != null) {
			params.addValue("ownerId", ownerId);
		}
		jdbc.query("select b.status, count(*) as n from backlinks b where 1 = 1" + owner(ownerId) + " group by b.status",
				params, rs -> {
					counts.put(BacklinkStatus.valueOf(rs.getString("status")), rs.getLong("n"));
				});
		return counts;
	}

	/**
	 * One select per stage date (each an index range scan on that column), grouped by {@code group}, glued with UNION
	 * ALL. {@code select} and {@code group} use {@code %1$s} for the stage's date column.
	 */
	private static String union(String select, String group, String join, String where) {
		List<String> parts = new ArrayList<>();
		for (StageKind kind : StageKind.values()) {
			String part = "select '" + kind.name() + "' as kind, " + select + ", count(*) as n from backlinks b" + join
					+ " where b.%1$s between :from and :to" + where + " group by " + group;
			parts.add(part.formatted(kind.column));
		}
		return String.join(" union all ", parts);
	}

	private static String owner(Long ownerId) {
		return ownerId == null ? "" : " and b.owner_id = :ownerId";
	}

	private static MapSqlParameterSource params(MarketingPeriod from, MarketingPeriod to, Long ownerId) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from.firstDay())
			.addValue("to", to.lastDay());
		if (ownerId != null) {
			params.addValue("ownerId", ownerId);
		}
		return params;
	}

}
