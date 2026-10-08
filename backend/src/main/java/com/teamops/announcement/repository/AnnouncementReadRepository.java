package com.teamops.announcement.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Read and acknowledgement receipts. Writes are idempotent; the first read and first acknowledgement are kept. */
@Repository
@RequiredArgsConstructor
public class AnnouncementReadRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public record Receipt(Instant readAt, Instant acknowledgedAt) {

	}

	public record Counts(long read, long acknowledged) {

		public static final Counts EMPTY = new Counts(0, 0);

	}

	public void markRead(Long announcementId, Long userId, Instant now) {
		jdbc.update("""
				insert ignore into announcement_reads (announcement_id, user_id, read_at)
				values (:announcementId, :userId, :now)
				""", params(announcementId, userId, now));
	}

	public void acknowledge(Long announcementId, Long userId, Instant now) {
		jdbc.update("""
				insert into announcement_reads (announcement_id, user_id, read_at, acknowledged_at)
				values (:announcementId, :userId, :now, :now)
				on duplicate key update acknowledged_at = coalesce(acknowledged_at, :now)
				""", params(announcementId, userId, now));
	}

	/** The user's receipts for the given announcements. */
	public Map<Long, Receipt> receipts(Long userId, Collection<Long> announcementIds) {
		Map<Long, Receipt> result = new HashMap<>();
		if (announcementIds.isEmpty()) {
			return result;
		}
		jdbc.query("""
				select announcement_id, read_at, acknowledged_at from announcement_reads
				where user_id = :userId and announcement_id in (:ids)
				""", new MapSqlParameterSource("userId", userId).addValue("ids", announcementIds), rs -> {
			Timestamp ack = rs.getTimestamp("acknowledged_at");
			result.put(rs.getLong("announcement_id"),
					new Receipt(rs.getTimestamp("read_at").toInstant(), ack == null ? null : ack.toInstant()));
		});
		return result;
	}

	/** Read and acknowledgement counts per announcement. */
	public Map<Long, Counts> counts(Collection<Long> announcementIds) {
		Map<Long, Counts> result = new HashMap<>();
		if (announcementIds.isEmpty()) {
			return result;
		}
		jdbc.query("""
				select announcement_id, count(*) as reads_count,
				  sum(case when acknowledged_at is not null then 1 else 0 end) as acks
				from announcement_reads where announcement_id in (:ids) group by announcement_id
				""", new MapSqlParameterSource("ids", announcementIds), rs -> {
			result.put(rs.getLong("announcement_id"), new Counts(rs.getLong("reads_count"), rs.getLong("acks")));
		});
		return result;
	}

	private static MapSqlParameterSource params(Long announcementId, Long userId, Instant now) {
		return new MapSqlParameterSource("announcementId", announcementId).addValue("userId", userId)
			.addValue("now", Timestamp.from(now));
	}

}
