package com.teamops.common.sequence;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Human-readable codes such as {@code TSK-000042}. The sequence row is locked with {@code SELECT ... FOR UPDATE} in
 * the caller's transaction, so concurrent creates never get the same code, and a rolled-back create does not burn a
 * number.
 */
@Service
@RequiredArgsConstructor
public class CodeGenerator {

	public static final String TASK = "TASK";

	public static final String PROJECT = "PROJECT";

	private final JdbcTemplate jdbcTemplate;

	@Transactional(propagation = Propagation.MANDATORY)
	public String next(String sequenceName) {
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"select prefix, pad_length, next_value from code_sequences where name = ? for update", sequenceName);
		long value = ((Number) row.get("next_value")).longValue();
		int pad = ((Number) row.get("pad_length")).intValue();
		jdbcTemplate.update("update code_sequences set next_value = ? where name = ?", value + 1, sequenceName);
		return format((String) row.get("prefix"), pad, value);
	}

	static String format(String prefix, int pad, long value) {
		return prefix + "-" + String.format("%0" + pad + "d", value);
	}

}
