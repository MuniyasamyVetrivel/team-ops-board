package com.teamops.finance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.SQLRestriction;

import com.teamops.common.persistence.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A manual exchange rate to AUD, the base currency: one unit of {@code currency} is {@code rateToAud} AUD from
 * {@code effectiveFrom} until the currency's next rate. Unique per currency and date. AUD itself is never stored.
 */
@Getter
@Setter
@Entity
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@Table(name = "finance_exchange_rates")
public class ExchangeRate extends SoftDeletableEntity {

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "rate_to_aud", nullable = false, precision = 18, scale = 8)
	private BigDecimal rateToAud;

	/** Where the rate came from, e.g. "RBA 4pm rate". */
	@Column(name = "source", length = 150)
	private String source;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

}
