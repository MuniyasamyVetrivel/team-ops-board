package com.teamops.marketing.email.service;

import java.util.ArrayList;
import java.util.List;

/**
 * An email campaign's raw counts (or the sum over several campaigns). The checks mirror the table's CHECK
 * constraints, so bad numbers are explained field by field instead of failing in the database.
 */
public record EmailCounts(int emailsSent, int delivered, int bounced, int opened, int uniqueOpens, int clicked,
		int uniqueClicks, int unsubscribed, int leads) {

	public static final EmailCounts ZERO = new EmailCounts(0, 0, 0, 0, 0, 0, 0, 0, 0);

	/** A problem with one count; {@code field} uses the API's field names. */
	public record Problem(String field, String message) {
	}

	/** Every inconsistency between the counts (empty when they make sense). */
	public List<Problem> problems() {
		List<Problem> problems = new ArrayList<>();
		require(problems, delivered <= emailsSent, "delivered", "Cannot be more than the emails sent");
		require(problems, bounced <= emailsSent, "bounced", "Cannot be more than the emails sent");
		require(problems, uniqueOpens <= opened, "uniqueOpens", "Cannot be more than the total opens");
		require(problems, uniqueOpens <= delivered, "uniqueOpens", "Cannot be more than the emails delivered");
		require(problems, uniqueClicks <= clicked, "uniqueClicks", "Cannot be more than the total clicks");
		require(problems, uniqueClicks <= delivered, "uniqueClicks", "Cannot be more than the emails delivered");
		require(problems, unsubscribed <= delivered, "unsubscribed", "Cannot be more than the emails delivered");
		return problems;
	}

	public boolean isZero() {
		return equals(ZERO);
	}

	public EmailCounts plus(EmailCounts other) {
		return new EmailCounts(emailsSent + other.emailsSent, delivered + other.delivered, bounced + other.bounced,
				opened + other.opened, uniqueOpens + other.uniqueOpens, clicked + other.clicked,
				uniqueClicks + other.uniqueClicks, unsubscribed + other.unsubscribed, leads + other.leads);
	}

	private static void require(List<Problem> problems, boolean ok, String field, String message) {
		if (!ok) {
			problems.add(new Problem(field, message));
		}
	}

}
