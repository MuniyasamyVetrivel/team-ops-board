package com.teamops.marketing.common;

/**
 * SEO ranking bucket for a position (brief section 26). A {@code null} position means the keyword is not ranked.
 * The UI pairs each tone with its label and an icon, never colour alone.
 */
public enum RankingStatus {

	/** Positions 1–10: green. */
	TOP_10("TOP 10", "GREEN"),
	/** Positions 11–100: orange. */
	RANKING("RANKING", "ORANGE"),
	/** No position: red. */
	NOT_RANKED("NOT RANKED", "RED");

	public static final int MIN_POSITION = 1;

	public static final int MAX_POSITION = 100;

	private static final int TOP_10_LIMIT = 10;

	private final String label;

	private final String tone;

	RankingStatus(String label, String tone) {
		this.label = label;
		this.tone = tone;
	}

	public String label() {
		return label;
	}

	public String tone() {
		return tone;
	}

	public static RankingStatus of(Integer position) {
		if (position == null) {
			return NOT_RANKED;
		}
		if (position < MIN_POSITION || position > MAX_POSITION) {
			throw new IllegalArgumentException("Ranking position must be between 1 and 100: " + position);
		}
		return position <= TOP_10_LIMIT ? TOP_10 : RANKING;
	}

}
