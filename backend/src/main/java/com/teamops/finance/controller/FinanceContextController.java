package com.teamops.finance.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.service.FinanceCurrencies;

import lombok.RequiredArgsConstructor;

/** What the Finance Tracker's forms start from: today's business date, the base and default currencies, the choices. */
@RestController
@RequestMapping(FinanceModule.API + "/context")
@PreAuthorize("hasAuthority('FINANCE_VIEW')")
@RequiredArgsConstructor
public class FinanceContextController {

	private final FinanceCurrencies currencies;

	private final BusinessCalendar calendar;

	/**
	 * @param defaultCurrency new licences and expenses start with it (Admin settings → Finance → Default currency)
	 * @param currencies INR, AUD and USD, then every currency in the exchange rates table
	 */
	public record FinanceContext(String today, String baseCurrency, String defaultCurrency, List<String> currencies) {
	}

	@GetMapping
	public FinanceContext context() {
		return new FinanceContext(calendar.today().toString(), FinanceModule.BASE_CURRENCY, currencies.defaultCurrency(),
				currencies.options());
	}

}
