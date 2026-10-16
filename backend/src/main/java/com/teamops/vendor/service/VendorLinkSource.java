package com.teamops.vendor.service;

import java.util.List;

import com.teamops.common.security.AuthenticatedUser;

/**
 * Records in other modules that point at a vendor, for the vendor page ("its contracts, linked licences and expenses").
 * Phase X8 adds one bean for tools and licences and one for expenses; the vendor module lists whatever beans exist
 * ({@code GET /api/modules/vendors/vendors/{id}/links}) and refuses to delete a vendor that live records still use.
 * <p>
 * A source decides who may see its records: finance data stays hidden from users without FINANCE_VIEW, so a finance
 * source returns {@link #visibleTo} false for them and its group is left out entirely.
 */
public interface VendorLinkSource {

	/** Stable identifier, e.g. {@code LICENCES}. */
	String kind();

	/** Shown as the group's heading, e.g. "Tools and licences". */
	String label();

	/** Whether the viewer may see this source's records at all (its own permission). */
	boolean visibleTo(AuthenticatedUser viewer);

	/** The newest records linked to the vendor, at most {@code limit}, with the total count. */
	Links linksFor(Long vendorId, int limit, AuthenticatedUser viewer);

	/** Live records using the vendor; a vendor is not deleted while any source counts one. */
	long countUsing(Long vendorId);

	/** @param total all records linked to the vendor; {@code items} holds the newest of them */
	record Links(long total, List<Link> items) {

	}

	/**
	 * One linked record, already formatted by its module. {@code detail} is a short secondary line (for example
	 * "12 of 20 seats · renews 1 Mar 2027"); {@code status} is the record's status code.
	 */
	record Link(Long id, String title, String detail, String status) {

	}

	/** A source's records as the vendor page shows them. */
	record LinkGroup(String kind, String label, long total, List<Link> items) {

	}

}
