package com.teamops.finance.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.finance.entity.ExchangeRate;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long>, JpaSpecificationExecutor<ExchangeRate> {

	boolean existsByCurrencyAndEffectiveFrom(String currency, LocalDate effectiveFrom);

	boolean existsByCurrencyAndEffectiveFromAndIdNot(String currency, LocalDate effectiveFrom, Long id);

	/**
	 * Every rate of these currencies in force on or before {@code date}, newest first per currency; the first of each
	 * currency is the one in force on that day.
	 */
	@Query("""
			select r from ExchangeRate r
			where r.currency in :currencies and r.effectiveFrom <= :date
			order by r.currency asc, r.effectiveFrom desc
			""")
	List<ExchangeRate> findInForceOnOrBefore(@Param("currencies") Collection<String> currencies,
			@Param("date") LocalDate date);

	/** The currencies with at least one live rate, in alphabetical order. */
	@Query("select distinct r.currency from ExchangeRate r order by r.currency")
	List<String> findCurrencies();

	/** Every rate on or before {@code date}, newest first per currency (the current rates table). */
	@Query("""
			select r from ExchangeRate r where r.effectiveFrom <= :date order by r.currency asc, r.effectiveFrom desc
			""")
	List<ExchangeRate> findAllOnOrBefore(@Param("date") LocalDate date);

}
