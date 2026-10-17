package com.teamops.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.finance.dto.FinanceDtos.Conversion;
import com.teamops.finance.entity.ExchangeRate;
import com.teamops.finance.repository.ExchangeRateRepository;
import com.teamops.recyclebin.service.RecycleBinService;

/** Rates to AUD: the newest rate on or before the day applies; AUD is always 1 and never stored. */
class ExchangeRateServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

	private final ExchangeRateRepository repository = mock(ExchangeRateRepository.class);

	private final ExchangeRateService service = new ExchangeRateService(repository, mock(AuditService.class),
			mock(RecycleBinService.class),
			new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"));

	@Test
	void theNewestRateOnOrBeforeTheDayApplies() {
		when(repository.findInForceOnOrBefore(anyCollection(), eq(TODAY))).thenReturn(List.of(
				rate(1L, "INR", "2026-10-01", "0.01820000"), rate(2L, "INR", "2026-09-01", "0.01790000"),
				rate(3L, "USD", "2026-08-15", "1.52000000")));

		var rates = service.ratesOn(Set.of("INR", "USD", "AUD"), TODAY);

		assertThat(rates).containsOnlyKeys("INR", "USD");
		assertThat(rates.get("INR").getRateToAud()).isEqualByComparingTo("0.0182");
	}

	@Test
	void conversionsRoundToTheCentAndSayWhichRateWasUsed() {
		when(repository.findInForceOnOrBefore(anyCollection(), eq(TODAY)))
			.thenReturn(List.of(rate(1L, "INR", "2026-10-01", "0.01820000")));

		Conversion conversion = service.convert(new BigDecimal("42000.00"), "inr", null);

		assertThat(conversion.currency()).isEqualTo("INR");
		assertThat(conversion.aud()).isEqualByComparingTo("764.40");
		assertThat(conversion.rateFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(ExchangeRateService.toAud(new BigDecimal("10.00"), new BigDecimal("0.33333333"))).isEqualByComparingTo("3.33");
	}

	@Test
	void audIsAlwaysOneAndNeedsNoRate() {
		Conversion conversion = service.convert(new BigDecimal("99.95"), "AUD", TODAY);

		assertThat(conversion.aud()).isEqualByComparingTo("99.95");
		assertThat(conversion.rate()).isEqualByComparingTo("1");
		assertThat(service.rateOn("AUD", TODAY)).contains(BigDecimal.ONE);
		verifyNoInteractions(repository);
		assertThatThrownBy(() -> ExchangeRateService.checkCurrency("aud"))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("BASE_CURRENCY"));
	}

	@Test
	void aCurrencyWithoutARateYetCannotBeConverted() {
		when(repository.findInForceOnOrBefore(anyCollection(), eq(TODAY))).thenReturn(List.of());

		assertThatThrownBy(() -> service.convert(BigDecimal.TEN, "SGD", null))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("NO_EXCHANGE_RATE"));
	}

	@Test
	void eachRateSaysHowManyUnitsOneAudBuys() {
		var dto = ExchangeRateService.toDto(rate(1L, "INR", "2026-10-01", "0.01820000"), true);
		assertThat(dto.unitsPerAud()).isEqualByComparingTo("54.945055");
		assertThat(dto.inForce()).isTrue();
	}

	static ExchangeRate rate(Long id, String currency, String from, String rateToAud) {
		ExchangeRate rate = new ExchangeRate();
		ReflectionTestUtils.setField(rate, "id", id);
		rate.setCurrency(currency);
		rate.setEffectiveFrom(LocalDate.parse(from));
		rate.setRateToAud(new BigDecimal(rateToAud));
		return rate;
	}

}
