package com.teamops.knowledge.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Pure text helpers for the knowledge base: slugs, safe full-text queries and plain-text excerpts. */
public final class KnowledgeText {

	/** InnoDB's default innodb_ft_min_token_size: shorter words are not indexed. */
	static final int MIN_TOKEN = 3;

	static final int MAX_SLUG = 250;

	private KnowledgeText() {
	}

	/** "How to reset your VPN password?" → "how-to-reset-your-vpn-password". Never empty. */
	public static String slugify(String title) {
		String ascii = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
		if (slug.length() > MAX_SLUG) {
			slug = slug.substring(0, MAX_SLUG).replaceAll("-+$", "");
		}
		return slug.isEmpty() ? "article" : slug;
	}

	/**
	 * A boolean-mode query requiring every word as a prefix ("+vpn* +pass*"), or {@code null} when no word is long
	 * enough to be indexed. Operators typed by the user are stripped, so the expression is always well-formed.
	 */
	public static String booleanQuery(String search) {
		if (search == null) {
			return null;
		}
		String query = Arrays.stream(search.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
			.filter(word -> word.length() >= MIN_TOKEN)
			.limit(8)
			.map(word -> "+" + word + "*")
			.collect(Collectors.joining(" "));
		return query.isEmpty() ? null : query;
	}

	/** First {@code max} characters of the article as plain text (Markdown syntax removed). */
	public static String excerpt(String markdown, int max) {
		String text = markdown.replaceAll("```[\\s\\S]*?```", " ")
			.replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
			.replaceAll("[#>*_`~|-]+", " ")
			.replaceAll("\\s+", " ")
			.trim();
		return text.length() <= max ? text : text.substring(0, max - 1).trim() + "…";
	}

}
