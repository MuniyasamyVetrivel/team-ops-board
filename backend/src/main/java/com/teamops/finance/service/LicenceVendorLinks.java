package com.teamops.finance.service;

import org.springframework.stereotype.Component;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.FinanceAccess;
import com.teamops.vendor.service.VendorLinkSource;

import lombok.RequiredArgsConstructor;

/**
 * Tools and licences on the vendor page (brief X7: "a vendor page shows its contracts, linked licences and
 * expenses"). Finance data: only FINANCE_VIEW holders see the group. Live licences keep their vendor from being
 * deleted.
 */
@Component
@RequiredArgsConstructor
class LicenceVendorLinks implements VendorLinkSource {

	private final LicenceService licences;

	@Override
	public String kind() {
		return "LICENCES";
	}

	@Override
	public String label() {
		return "Tools and licences";
	}

	@Override
	public boolean visibleTo(AuthenticatedUser viewer) {
		return FinanceAccess.canView(viewer);
	}

	@Override
	public Links linksFor(Long vendorId, int limit, AuthenticatedUser viewer) {
		return licences.linksForVendor(vendorId, limit);
	}

	@Override
	public long countUsing(Long vendorId) {
		return licences.countUsingVendor(vendorId);
	}

}
