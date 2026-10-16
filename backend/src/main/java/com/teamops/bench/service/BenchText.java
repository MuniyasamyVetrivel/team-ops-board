package com.teamops.bench.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.util.StringUtils;

import com.teamops.common.exception.ApiException;

/** Input clean-up shared by the bench services and the consultant import. */
public final class BenchText {

	public static final int MAX_SKILLS = 50;

	public static final int SKILL_LENGTH = 100;

	public static final int MAX_CERTIFICATIONS = 30;

	public static final int CERTIFICATION_LENGTH = 200;

	/** How a list is written in one CSV cell: "SAP S/4HANA; Tosca". */
	public static final String LIST_SEPARATOR = ";";

	private BenchText() {
	}

	/** Trimmed text, or null when blank. */
	static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	/** A case-insensitive "contains" LIKE pattern with {@code \} as the escape character. */
	static String containsPattern(String search) {
		return "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
				+ "%";
	}

	static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * Skills as entered: trimmed, blanks dropped, repeats (ignoring case) dropped keeping the first spelling and the
	 * order. 400 when there are too many or one is too long.
	 */
	public static List<String> skills(List<String> names) {
		List<String> skills = distinct(names);
		if (skills.size() > MAX_SKILLS) {
			throw ApiException.badRequest("TOO_MANY_SKILLS", "A consultant can have at most " + MAX_SKILLS + " skills");
		}
		for (String skill : skills) {
			if (skill.length() > SKILL_LENGTH) {
				throw ApiException.badRequest("SKILL_TOO_LONG",
						"A skill can be at most " + SKILL_LENGTH + " characters: " + skill);
			}
		}
		return skills;
	}

	/** "SAP S/4HANA; Tosca;; tosca" → ["SAP S/4HANA", "Tosca"]. */
	public static List<String> splitList(String cell) {
		if (cell == null) {
			return List.of();
		}
		return distinct(List.of(cell.split(LIST_SEPARATOR)));
	}

	public static String joinList(List<String> values) {
		return String.join(LIST_SEPARATOR + " ", values);
	}

	private static List<String> distinct(List<String> values) {
		if (values == null) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String value : values) {
			String trimmed = trimToNull(value);
			if (trimmed != null && seen.add(key(trimmed))) {
				result.add(trimmed);
			}
		}
		return List.copyOf(result);
	}

}
