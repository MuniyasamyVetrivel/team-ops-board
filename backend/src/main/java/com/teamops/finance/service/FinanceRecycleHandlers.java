package com.teamops.finance.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.common.exception.ApiException;
import com.teamops.finance.entity.ExchangeRate;
import com.teamops.finance.entity.FinanceLicence;
import com.teamops.recyclebin.service.RecycleBinHandler;
import com.teamops.vendor.repository.VendorRepository;

/**
 * Finance records in the recycle bin. A licence keeps its assigned users (they go with its permanent delete through
 * the foreign key cascade), is filed under its department, and comes back only while its vendor is live. A rate is
 * named after its currency and date.
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
