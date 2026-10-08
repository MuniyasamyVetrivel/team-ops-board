package com.teamops.marketing.lead.service;

import java.time.LocalDate;
import java.util.Optional;

import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.lead.repository.LeadQuery.LinkKind;

/**
 * Which campaign or content a lead of each source may name (brief sections 43–44): an email lead its email campaign,
 * a LinkedIn or paid lead its paid campaign, a blog lead the content item. Other sources have no link.
 */
public final class LeadRules {

	private LeadRules() {
	}

	/** The kind of link a source takes, if any. */
	public static Optional<LinkKind> linkFor(LeadSource source) {
		return Optional.ofNullable(switch (source) {
			case EMAIL -> LinkKind.EMAIL_CAMPAIGN;
			case LINKEDIN, PAID_CAMPAIGN -> LinkKind.PAID_CAMPAIGN;
			case BLOG -> LinkKind.CONTENT;
			case ORGANIC, WEBSITE, REFERRAL, OTHER -> null;
		});
	}

	public static boolean accepts(LeadSource source, LinkKind kind) {
		return linkFor(source).filter(kind::equals).isPresent();
	}

	/** A lead cannot come in before the campaign was sent, started or the content was published. */
	public static boolean before(LocalDate leadDate, LocalDate linkDate) {
		return linkDate != null && leadDate.isBefore(linkDate);
	}

}
