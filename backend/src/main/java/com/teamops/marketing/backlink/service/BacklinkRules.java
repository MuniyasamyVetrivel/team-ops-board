package com.teamops.marketing.backlink.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.backlink.entity.BacklinkDates;
import com.teamops.marketing.backlink.entity.BacklinkStatus;
import com.teamops.marketing.common.MarketingMath;

/**
 * The stage rules of a backlink (brief sections 45–46). Each status needs the dates of the stages it went through and
 * allows no later ones: SUBMITTED needs the submitted date, APPROVED the submitted and approved dates, LIVE the
 * submitted and live dates (approval is optional, some sites publish directly), REJECTED the submitted and rejected
 * dates, LOST the submitted, live and lost dates. Stages happen in order and never in the future, and a live or lost
 * link needs its URL.
 */
public final class BacklinkRules {

	/** A stage of the backlink, in order, with its date field. */
	enum Stage {

		SUBMITTED("submittedDate", "submitted"), APPROVED("approvedDate", "approved"), LIVE("liveDate", "live"),
		REJECTED("rejectedDate", "rejected"), LOST("lostDate", "lost");

		final String field;

		final String verb;

		Stage(String field, String verb) {
			this.field = field;
			this.verb = verb;
		}

	}

	private BacklinkRules() {
	}

	static Set<Stage> required(BacklinkStatus status) {
		return switch (status) {
			case PROSPECTED -> EnumSet.noneOf(Stage.class);
			case SUBMITTED -> EnumSet.of(Stage.SUBMITTED);
			case APPROVED -> EnumSet.of(Stage.SUBMITTED, Stage.APPROVED);
			case LIVE -> EnumSet.of(Stage.SUBMITTED, Stage.LIVE);
			case REJECTED -> EnumSet.of(Stage.SUBMITTED, Stage.REJECTED);
			case LOST -> EnumSet.of(Stage.SUBMITTED, Stage.LIVE, Stage.LOST);
		};
	}

	static Set<Stage> allowed(BacklinkStatus status) {
		return switch (status) {
			case PROSPECTED -> EnumSet.noneOf(Stage.class);
			case SUBMITTED -> EnumSet.of(Stage.SUBMITTED);
			case APPROVED -> EnumSet.of(Stage.SUBMITTED, Stage.APPROVED);
			case LIVE -> EnumSet.of(Stage.SUBMITTED, Stage.APPROVED, Stage.LIVE);
			case REJECTED -> EnumSet.of(Stage.SUBMITTED, Stage.APPROVED, Stage.REJECTED);
			case LOST -> EnumSet.of(Stage.SUBMITTED, Stage.APPROVED, Stage.LIVE, Stage.LOST);
		};
	}

	/** Checks the dates against the status; 400 with a stable code otherwise. */
	public static void check(BacklinkStatus status, BacklinkDates dates, String linkUrl, LocalDate today) {
		Set<Stage> required = required(status);
		Set<Stage> allowed = allowed(status);
		for (Stage stage : Stage.values()) {
			LocalDate date = date(dates, stage);
			if (date == null && required.contains(stage)) {
				throw ApiException.badRequest("MISSING_DATE",
						"%s backlink needs its %s date".formatted(article(status), stage.verb));
			}
			if (date != null && !allowed.contains(stage)) {
				throw ApiException.badRequest("DATE_NOT_ALLOWED",
						"%s backlink has no %s date".formatted(article(status), stage.verb));
			}
			if (date != null && date.isAfter(today)) {
				throw ApiException.badRequest("FUTURE_DATE",
						"The %s date cannot be in the future".formatted(stage.verb));
			}
		}
		requireOrder(dates.submitted(), dates.approved(), "approved", "submitted");
		requireOrder(dates.submitted(), dates.live(), "live", "submitted");
		requireOrder(dates.approved(), dates.live(), "live", "approved");
		requireOrder(dates.submitted(), dates.rejected(), "rejected", "submitted");
		requireOrder(dates.approved(), dates.rejected(), "rejected", "approved");
		requireOrder(dates.live(), dates.lost(), "lost", "live");
		if ((status == BacklinkStatus.LIVE || status == BacklinkStatus.LOST) && linkUrl == null) {
			throw ApiException.badRequest("LINK_URL_REQUIRED", "A live backlink needs the URL of the page that links to us");
		}
	}

	/**
	 * The dates after a move to {@code status} on {@code date}: stages the status needs and has not reached yet are
	 * dated {@code date}, stages it does not allow are cleared, and the others keep their dates.
	 */
	public static BacklinkDates moveTo(BacklinkStatus status, BacklinkDates current, LocalDate date) {
		Set<Stage> required = required(status);
		Set<Stage> allowed = allowed(status);
		LocalDate[] next = new LocalDate[Stage.values().length];
		for (Stage stage : Stage.values()) {
			LocalDate existing = date(current, stage);
			next[stage.ordinal()] = !allowed.contains(stage) ? null
					: existing == null && required.contains(stage) ? date : existing;
		}
		return new BacklinkDates(next[0], next[1], next[2], next[3], next[4]);
	}

	/** Brief section 46: what is still to be submitted this month, max(target − submitted, 0) (50 − 35 = 15). */
	public static BigDecimal remaining(BigDecimal target, long submitted) {
		return MarketingMath.remaining(target, submitted);
	}

	/**
	 * The referring site's host in lower case without "www.", from a domain or a URL: "https://www.DZone.com/x" →
	 * "dzone.com". Null when it is not a usable host.
	 */
	public static String domainOf(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.trim();
		String host;
		try {
			host = new URI(text.contains("://") ? text : "https://" + text).getHost();
		}
		catch (URISyntaxException ex) {
			return null;
		}
		if (host == null || !host.contains(".")) {
			return null;
		}
		host = host.toLowerCase(Locale.ROOT);
		return host.startsWith("www.") ? host.substring(4) : host;
	}

	static LocalDate date(BacklinkDates dates, Stage stage) {
		return switch (stage) {
			case SUBMITTED -> dates.submitted();
			case APPROVED -> dates.approved();
			case LIVE -> dates.live();
			case REJECTED -> dates.rejected();
			case LOST -> dates.lost();
		};
	}

	private static void requireOrder(LocalDate earlier, LocalDate later, String laterVerb, String earlierVerb) {
		if (earlier != null && later != null && later.isBefore(earlier)) {
			throw ApiException.badRequest("DATE_ORDER",
					"The %s date cannot be before the %s date".formatted(laterVerb, earlierVerb));
		}
	}

	/** "A live", "An approved". */
	private static String article(BacklinkStatus status) {
		String label = status.name().toLowerCase(Locale.ROOT);
		return ("aeiou".indexOf(label.charAt(0)) >= 0 ? "An " : "A ") + label;
	}

}
