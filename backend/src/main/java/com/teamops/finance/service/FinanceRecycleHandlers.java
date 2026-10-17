package com.teamops.finance.service;

import java.time.YearMonth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.common.exception.ApiException;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.finance.entity.ExchangeRate;
import com.teamops.finance.entity.FinanceBudget;
import com.teamops.finance.entity.FinanceExpense;
import com.teamops.finance.entity.FinanceLicence;
import com.teamops.recyclebin.service.RecycleBinHandler;
import com.teamops.vendor.repository.VendorRepository;

/**
 * Finance records in the recycle bin. A licence keeps its assigned users (they go with its permanent delete through
 * the foreign key cascade), is filed under its department, and comes back only while its vendor is live. A rate is
 * named after its currency and date. An expense keeps its receipt until it is purged, when the stored file goes too,
 * and comes back only while its vendor is live. A budget is named after its department and month.
 */
@Configuration
class FinanceRecycleHandlers {

	@Bean
	RecycleBinHandler<FinanceLicence> financeLicenceRecycleHandler(VendorRepository vendors) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<FinanceLicence> type() {
				return FinanceLicence.class;
			}

			@Override
			public Long departmentOf(FinanceLicence licence) {
				return licence.getDepartment() == null ? null : licence.getDepartment().getId();
			}

			@Override
			public void checkRestore(FinanceLicence licence) {
				if (licence.getVendorId() != null && !vendors.existsById(licence.getVendorId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"This licence's vendor is in the recycle bin. Restore the vendor first.");
				}
			}

		};
	}

	@Bean
	RecycleBinHandler<FinanceExpense> financeExpenseRecycleHandler(VendorRepository vendors, FileService fileService) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<FinanceExpense> type() {
				return FinanceExpense.class;
			}

			@Override
			public String label(FinanceExpense expense) {
				return expense.getExpenseDate() + " " + expense.getDescription();
			}

			@Override
			public Long departmentOf(FinanceExpense expense) {
				return expense.getDepartment() == null ? null : expense.getDepartment().getId();
			}

			@Override
			public void checkRestore(FinanceExpense expense) {
				if (expense.getVendorId() != null && !vendors.existsById(expense.getVendorId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"This expense's vendor is in the recycle bin. Restore the vendor first.");
				}
			}

			/** The receipt goes with the permanent delete, once the row no longer points at it. */
			@Override
			public void afterPurge(FinanceExpense expense) {
				StoredFile receipt = expense.getReceipt();
				if (receipt != null) {
					fileService.delete(receipt);
				}
			}

		};
	}

	@Bean
	RecycleBinHandler<FinanceBudget> financeBudgetRecycleHandler() {
		return new RecycleBinHandler<>() {

			@Override
			public Class<FinanceBudget> type() {
				return FinanceBudget.class;
			}

			@Override
			public String label(FinanceBudget budget) {
				return budget.getDepartment().getName() + " budget " + YearMonth.of(budget.getYear(), budget.getMonth());
			}

			@Override
			public Long departmentOf(FinanceBudget budget) {
				return budget.getDepartment().getId();
			}

		};
	}

	@Bean
	RecycleBinHandler<ExchangeRate> exchangeRateRecycleHandler() {
		return new RecycleBinHandler<>() {

			@Override
			public Class<ExchangeRate> type() {
				return ExchangeRate.class;
			}

			@Override
			public String label(ExchangeRate rate) {
				return rate.getCurrency() + " rate from " + rate.getEffectiveFrom();
			}

		};
	}

}
