package com.teamops.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.dto.FinanceDtos.Conversion;
import com.teamops.finance.dto.FinanceDtos.ExchangeRateDto;
import com.teamops.finance.dto.FinanceDtos.SaveExchangeRate;
import com.teamops.finance.entity.ExchangeRate;
import com.teamops.finance.repository.ExchangeRateRepository;
import com.teamops.recyclebin.service.RecycleBinService;

import lombok.RequiredArgsConstructor;

/**
 * The manual exchange rates table (Phase X8): rates to AUD, the base currency, each in force from its date until the
 * currency's next rate. One rate per currency and date, including rates in the recycle bin. AUD is always 1 and never
 * stored. Licences (and, later, expenses) convert with {@link #ratesOn} and {@link #convert}. Changes are audited.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

	public static final String ENTITY = "EXCHANGE_RATE";

	public static final int EXPORT_LIMIT = 10_000;

	/** The export's columns: the same as the import's. */
	public static final List<String> CSV_COLUMNS = List.of("currency", "effective_from", "rate_to_aud", "source");

	private final ExchangeRateRepository repository;

	private final AuditService auditService;

	private final RecycleBinService recycleBin;

	private final BusinessCalendar calendar;

	public record RateFilter(String currency) {
	}

	@Transactional(readOnly = true)
	public PageResponse<ExchangeRateDto> search(RateFilter filter, Pageable pageable) {
		var page = repository.findAll(spec(filter), pageable);
		Set<Long> inForce = inForceIds(page.map(ExchangeRate::getCurrency).toSet());
		return PageResponse.of(page.map(rate -> toDto(rate, inForce.contains(rate.getId()))));
	}

	/** The rate in force today for every currency that has one, by currency. */
	@Transactional(readOnly = true)
	public List<ExchangeRateDto> current() {
		return latestPerCurrency(repository.findAllOnOrBefore(calendar.today())).values()
			.stream()
			.map(rate -> toDto(rate, true))
			.toList();
	}

	@Transactional(readOnly = true)
	public ExchangeRateDto get(Long id) {
		ExchangeRate rate = load(id);
		return toDto(rate, inForceIds(Set.of(rate.getCurrency())).contains(id));
	}

	/** The rates in force on {@code date} for these currencies (AUD left out: it is always 1); missing ones absent. */
	@Transactional(readOnly = true)
	public Map<String, ExchangeRate> ratesOn(Collection<String> currencies, LocalDate date) {
		Set<String> wanted = currencies.stream()
			.filter(Objects::nonNull)
			.filter(currency -> !FinanceModule.BASE_CURRENCY.equals(currency))
			.collect(Collectors.toSet());
		return wanted.isEmpty() ? Map.of() : latestPerCurrency(repository.findInForceOnOrBefore(wanted, date));
	}

	/** The rate in force on {@code date}: 1 for AUD, empty when the currency has no rate by then. */
	@Transactional(readOnly = true)
	public Optional<BigDecimal> rateOn(String currency, LocalDate date) {
		if (FinanceModule.BASE_CURRENCY.equals(currency)) {
			return Optional.of(BigDecimal.ONE);
		}
		return Optional.ofNullable(ratesOn(List.of(currency), date).get(currency)).map(ExchangeRate::getRateToAud);
	}

	/** {@code amount} in AUD at the rate in force on {@code date} (today when null); 400 NO_EXCHANGE_RATE without one. */
	@Transactional(readOnly = true)
	public Conversion convert(BigDecimal amount, String rawCurrency, LocalDate date) {
		String currency = FinanceText.currency(rawCurrency, FinanceModule.BASE_CURRENCY);
		LocalDate on = date == null ? calendar.today() : date;
		if (FinanceModule.BASE_CURRENCY.equals(currency)) {
			return new Conversion(amount, currency, on, BigDecimal.ONE, null, toAud(amount, BigDecimal.ONE));
		}
		ExchangeRate rate = ratesOn(List.of(currency), on).get(currency);
		if (rate == null) {
			throw ApiException.badRequest("NO_EXCHANGE_RATE",
					"There is no " + currency + " rate on or before " + on + ". Add one under Exchange rates.");
		}
		return new Conversion(amount, currency, on, rate.getRateToAud(), rate.getEffectiveFrom(),
				toAud(amount, rate.getRateToAud()));
	}

	/** {@code amount} × {@code rate}, to the cent. */
	public static BigDecimal toAud(BigDecimal amount, BigDecimal rate) {
		return amount == null || rate == null ? null : amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
	}

	@Transactional
	public ExchangeRateDto create(SaveExchangeRate request, AuthenticatedUser actor, ClientInfo info) {
		ExchangeRate rate = new ExchangeRate();
		apply(rate, request);
		ExchangeRate saved = repository.saveAndFlush(rate);
		auditService.record(AuditAction.EXCHANGE_RATE_CREATED, actor.id(), ENTITY, saved.getId(),
				Map.of("currency", saved.getCurrency(), "effectiveFrom", saved.getEffectiveFrom(), "rateToAud",
						saved.getRateToAud()),
				info);
		return get(saved.getId());
	}

	@Transactional
	public ExchangeRateDto update(Long id, SaveExchangeRate request, AuthenticatedUser actor, ClientInfo info) {
		ExchangeRate rate = load(id);
		if (request.version() == null || !Objects.equals(rate.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this rate just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(rate);
		apply(rate, request);
		AuditChanges changes = new AuditChanges();
		snapshot(rate).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.EXCHANGE_RATE_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), info);
		}
		return get(id);
	}

	/** Moves the rate to the recycle bin; amounts converted at it later use the currency's previous rate. Audited. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo info) {
		ExchangeRate rate = load(id);
		recycleBin.moveToBin(rate, actor);
		auditService.record(AuditAction.EXCHANGE_RATE_DELETED, actor.id(), ENTITY, id,
				Map.of("currency", rate.getCurrency(), "effectiveFrom", rate.getEffectiveFrom()), info);
	}

	/** CSV in the import's columns. */
	@Transactional(readOnly = true)
	public byte[] export(RateFilter filter) {
		List<List<String>> rows = repository.findAll(spec(filter), Sort.by("currency", "effectiveFrom", "id"))
			.stream()
			.limit(EXPORT_LIMIT)
			.map(r -> List.of(r.getCurrency(), r.getEffectiveFrom().toString(), r.getRateToAud().toPlainString(),
					r.getSource() == null ? "" : r.getSource()))
			.toList();
		return CsvWriter.write(CSV_COLUMNS, rows);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	/** 400 when the currency is AUD (the base, always 1); 409 when the currency already has a rate on that date. */
	static String checkCurrency(String rawCurrency) {
		String currency = FinanceText.currency(rawCurrency, null);
		if (FinanceModule.BASE_CURRENCY.equals(currency)) {
			throw ApiException.badRequest("BASE_CURRENCY", "AUD is the base currency: its rate is always 1");
		}
		return currency;
	}

	private void apply(ExchangeRate rate, SaveExchangeRate request) {
		String currency = checkCurrency(request.currency());
		LocalDate from = request.effectiveFrom();
		boolean taken = rate.getId() == null ? repository.existsByCurrencyAndEffectiveFrom(currency, from)
				: repository.existsByCurrencyAndEffectiveFromAndIdNot(currency, from, rate.getId());
		if (taken) {
			throw ApiException.conflict("RATE_EXISTS", currency + " already has a rate from " + from + ". Edit that one.");
		}
		if (!currency.equals(rate.getCurrency()) || !from.equals(rate.getEffectiveFrom())) {
			recycleBin.requireNotInBin(ExchangeRate.class, Map.of("currency", currency, "effective_from", from),
					"The " + currency + " rate from " + from);
		}
		rate.setCurrency(currency);
		rate.setEffectiveFrom(from);
		rate.setRateToAud(request.rateToAud().setScale(8, RoundingMode.UNNECESSARY));
		rate.setSource(FinanceText.trimToNull(request.source()));
	}

	private static Map<String, Object> snapshot(ExchangeRate r) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("currency", r.getCurrency());
		values.put("effectiveFrom", r.getEffectiveFrom());
		values.put("rateToAud", r.getRateToAud());
		values.put("source", r.getSource());
		return values;
	}

	/** Ids of the rates in force today for these currencies. */
	private Set<Long> inForceIds(Set<String> currencies) {
		return ratesOn(currencies, calendar.today()).values()
			.stream()
			.map(ExchangeRate::getId)
			.collect(Collectors.toSet());
	}

	/** The first rate of each currency in a list ordered newest first per currency. */
	private static Map<String, ExchangeRate> latestPerCurrency(List<ExchangeRate> newestFirst) {
		Map<String, ExchangeRate> latest = new HashMap<>();
		newestFirst.forEach(rate -> latest.putIfAbsent(rate.getCurrency(), rate));
		return latest.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
	}

	static ExchangeRateDto toDto(ExchangeRate r, boolean inForce) {
		BigDecimal unitsPerAud = BigDecimal.ONE.divide(r.getRateToAud(), 6, RoundingMode.HALF_UP);
		return new ExchangeRateDto(r.getId(), r.getCurrency(), r.getEffectiveFrom(), r.getRateToAud(), unitsPerAud,
				r.getSource(), inForce, r.getVersion(), r.getCreatedAt(), r.getUpdatedAt());
	}

	private ExchangeRate load(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> ApiException.notFound("EXCHANGE_RATE_NOT_FOUND", "Exchange rate not found"));
	}

	private static Specification<ExchangeRate> spec(RateFilter f) {
		Specification<ExchangeRate> spec = (root, query, cb) -> cb.conjunction();
		String currency = FinanceText.trimToNull(f.currency());
		if (currency != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("currency"), currency.toUpperCase(Locale.ROOT)));
		}
		return spec;
	}

}
