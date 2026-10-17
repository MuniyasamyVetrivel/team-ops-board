package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.entity.ExchangeRate;
import com.teamops.finance.repository.ExchangeRateRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.entity.PermissionCodes;

/**
 * Imports exchange rates to AUD; the columns match the rates export. A currency that already has a rate on that date
 * (in the recycle bin too) is an error, and so is AUD itself: nothing is overwritten.
 */
@Component
public class ExchangeRateCsvImporter implements CsvImporter<ExchangeRateCsvImporter.Row> {

	public static final String TYPE = "exchange-rates";

	private static final Pattern CURRENCY = Pattern.compile("[A-Za-z]{3}");

	private static final BigDecimal MAX_RATE = new BigDecimal("1000000");

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("currency", "Three-letter code, not AUD", "INR"),
			CsvColumn.required("effective_from", "YYYY-MM-DD: the rate applies from this day", "2026-10-01"),
			CsvColumn.required("rate_to_aud", "AUD for one unit of the currency, up to 8 decimals", "0.01818"),
			CsvColumn.optional("source", "Where the rate came from", "RBA 4pm rate"));

	private final ExchangeRateRepository rates;

	private final RecycleBinService recycleBin;

	public ExchangeRateCsvImporter(ExchangeRateRepository rates, RecycleBinService recycleBin) {
		this.rates = rates;
		this.recycleBin = recycleBin;
	}

	public record Row(String currency, LocalDate effectiveFrom, BigDecimal rateToAud, String source) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public Area area() {
		return Area.FINANCE;
	}

	@Override
	public String label() {
		return "Exchange rates";
	}

	@Override
	public String description() {
		return "Rates to AUD by currency and date. Rows for a currency that already has a rate on that date are rejected.";
	}

	@Override
	public String permission() {
		return PermissionCodes.FINANCE_EDIT;
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Row> open(AuthenticatedUser actor) {
		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String raw = row.text("currency", 3);
				LocalDate from = row.date("effective_from");
				BigDecimal rate = row.amount("rate_to_aud", 8);
				String source = row.text("source", 150);
				String currency = raw == null ? null : raw.toUpperCase(Locale.ROOT);
				if (currency != null && !CURRENCY.matcher(currency).matches()) {
					row.error("currency", "Must be a three-letter code like INR or USD");
				}
				else if (FinanceModule.BASE_CURRENCY.equals(currency)) {
					row.error("currency", "AUD is the base currency: its rate is always 1");
				}
				if (rate != null && (rate.signum() == 0 || rate.compareTo(MAX_RATE) > 0)) {
					row.error("rate_to_aud", "Must be above 0 and at most 1,000,000");
				}
				if (!row.valid()) {
					return null;
				}
				if (rates.existsByCurrencyAndEffectiveFrom(currency, from)) {
					row.error("effective_from", currency + " already has a rate from this day");
					return null;
				}
				try {
					recycleBin.requireNotInBin(ExchangeRate.class, Map.of("currency", currency, "effective_from", from),
							"This rate");
				}
				catch (ApiException ex) {
					row.error("effective_from", ex.getMessage());
					return null;
				}
				return new Row(currency, from, rate.setScale(8, RoundingMode.UNNECESSARY), source);
			}

			@Override
			public String key(Row record) {
				return record.currency() + "|" + record.effectiveFrom();
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					ExchangeRate rate = new ExchangeRate();
					rate.setCurrency(record.currency());
					rate.setEffectiveFrom(record.effectiveFrom());
					rate.setRateToAud(record.rateToAud());
					rate.setSource(record.source());
					rates.save(rate);
				}
				return records.size();
			}

		};
	}

}
