package com.teamops.finance.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.dto.FinanceDtos.Conversion;
import com.teamops.finance.dto.FinanceDtos.ExchangeRateDto;
import com.teamops.finance.dto.FinanceDtos.SaveExchangeRate;
import com.teamops.finance.service.ExchangeRateService;
import com.teamops.finance.service.ExchangeRateService.RateFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.RequiredArgsConstructor;

/**
 * The manual exchange rates table: rates to AUD, the base currency. FINANCE_VIEW reads and converts, FINANCE_EDIT
 * changes ("editable by finance users"); the module switch applies to every endpoint.
 */
@RestController
@RequestMapping(FinanceModule.API + "/exchange-rates")
@PreAuthorize("hasAuthority('FINANCE_VIEW')")
@RequiredArgsConstructor
public class ExchangeRateController {

	static final Map<String, List<String>> SORTS = Map.of("currency", List.of("currency", "effectiveFrom", "id"),
			"effective", List.of("effectiveFrom", "currency", "id"), "updated", List.of("updatedAt", "id"));

	private final ExchangeRateService rateService;

	@GetMapping
	public PageResponse<ExchangeRateDto> search(@RequestParam(required = false) String currency,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
			@RequestParam(required = false) String sort) {
		return rateService.search(new RateFilter(currency), PageRequests.of(page, size, sort, SORTS, "effective,desc"));
	}

	/** The rate in force today for every currency that has one. */
	@GetMapping("/current")
	public List<ExchangeRateDto> current() {
		return rateService.current();
	}

	/** An amount in AUD at the rate in force on {@code date} (today when absent); 400 NO_EXCHANGE_RATE without one. */
	@GetMapping("/convert")
	public Conversion convert(@RequestParam @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal amount,
			@RequestParam String currency, @RequestParam(required = false) LocalDate date) {
		return rateService.convert(amount, currency, date);
	}

	/** CSV in the import's columns. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String currency) {
		return FinanceCsv.download("exchange-rates.csv", rateService.export(new RateFilter(currency)));
	}

	@GetMapping("/{id}")
	public ExchangeRateDto get(@PathVariable Long id) {
		return rateService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ResponseEntity<ExchangeRateDto> create(@Valid @RequestBody SaveExchangeRate request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(rateService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ExchangeRateDto update(@PathVariable Long id, @Valid @RequestBody SaveExchangeRate request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return rateService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('FINANCE_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		rateService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
