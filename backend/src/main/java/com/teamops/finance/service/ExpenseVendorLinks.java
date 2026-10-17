package com.teamops.finance.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.FinanceAccess;
import com.teamops.vendor.service.VendorLinkSource;

import lombok.RequiredArgsConstructor;

/**
 * Expenses on the vendor page (brief X7: "a vendor page shows its contracts, linked licences and expenses"). Finance
 * data: only FINANCE_VIEW holders see the group. Live expenses keep their vendor from being deleted.
 */
@Component
@Order(2)
@RequiredArgsConstructor
class ExpenseVendorLinks implements VendorLinkSource {

	private final ExpenseService expenses;

	@Override
	public String kind() {
		return "EXPENSES";
	}

	@Override
	public String label() {
		return "Expenses";
	}

	@Override
	public boolean visibleTo(AuthenticatedUser viewer) {
		return FinanceAccess.canView(viewer);
	}

	@Override
	public Links linksFor(Long vendorId, int limit, AuthenticatedUser viewer) {
		return expenses.linksForVendor(vendorId, limit);
	}

	@Override
	public long countUsing(Long vendorId) {
		return expenses.countUsingVendor(vendorId);
	}

}
