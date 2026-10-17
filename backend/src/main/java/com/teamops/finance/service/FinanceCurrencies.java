package com.teamops.finance.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.settings.SettingChoices;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.repository.ExchangeRateRepository;

import lombok.RequiredArgsConstructor;

/**
 * The currencies the Finance Tracker offers: INR, AUD (the base) and USD always, then every currency in the exchange
 * rates table. They are the options of the "Default currency" setting (Admin settings → Finance), which new licences
 * and expenses start with; each record may still choose its own currency.
 */
@Component
@RequiredArgsConstructor
public class FinanceCurrencies implements SettingChoices {

	/** Always offered, whatever the rates table holds. */
	public static final List<String> STANDARD = List.of("INR", FinanceModule.BASE_CURRENCY, "USD");

	private final ExchangeRateRepository rates;

	private final AppSettingsService settings;

	@Override
	public String key() {
		return AppSettingsService.FINANCE_DEFAULT_CURRENCY;
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> options() {
		Set<String> currencies = new LinkedHashSet<>(STANDARD);
		currencies.addAll(rates.findCurrencies());
		return List.copyOf(currencies);
	}

	/** The currency a new licence or expense starts with. */
	public String defaultCurrency() {
		return settings.financeDefaultCurrency();
	}

}
