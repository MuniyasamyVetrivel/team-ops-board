package com.teamops.marketing.backlink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.backlink.entity.BacklinkDates;
import com.teamops.marketing.backlink.entity.BacklinkStatus;

class BacklinkRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

	private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

	private static final LocalDate OCT_3 = LocalDate.of(2026, 10, 3);

	private static final LocalDate OCT_6 = LocalDate.of(2026, 10, 6);

	private static final String LINK = "https://dzone.com/articles/sap-test-automation";

	@Test
	void briefSection46RemainingIsWhatIsStillToSubmit() {
		// Target 50, submitted 35 → 15 remaining; never below zero.
		assertThat(BacklinkRules.remaining(new BigDecimal("50"), 35)).isEqualByComparingTo("15");
		assertThat(BacklinkRules.remaining(new BigDecimal("50"), 60)).isEqualByComparingTo("0");
		assertThat(BacklinkRules.remaining(null, 35)).isNull();
	}

	@Test
	void eachStatusNeedsTheDatesOfItsStages() {
		assertOk(BacklinkStatus.PROSPECTED, BacklinkDates.NONE, null);
		assertOk(BacklinkStatus.SUBMITTED, dates(OCT_1, null, null, null, null), null);
		assertOk(BacklinkStatus.APPROVED, dates(OCT_1, OCT_3, null, null, null), null);
		assertOk(BacklinkStatus.LIVE, dates(OCT_1, OCT_3, OCT_6, null, null), LINK);
		// Some sites publish without a separate approval.
		assertOk(BacklinkStatus.LIVE, dates(OCT_1, null, OCT_6, null, null), LINK);
		assertOk(BacklinkStatus.REJECTED, dates(OCT_1, OCT_3, null, OCT_6, null), null);
		assertOk(BacklinkStatus.LOST, dates(OCT_1, null, OCT_3, null, OCT_6), LINK);

		assertCode(BacklinkStatus.SUBMITTED, BacklinkDates.NONE, null, "MISSING_DATE");
		assertCode(BacklinkStatus.APPROVED, dates(OCT_1, null, null, null, null), null, "MISSING_DATE");
		assertCode(BacklinkStatus.LIVE, dates(OCT_1, OCT_3, null, null, null), LINK, "MISSING_DATE");
		assertCode(BacklinkStatus.LOST, dates(OCT_1, null, OCT_3, null, null), LINK, "MISSING_DATE");
		assertThatThrownBy(() -> BacklinkRules.check(BacklinkStatus.APPROVED, dates(OCT_1, null, null, null, null), null, TODAY))
			.hasMessage("An approved backlink needs its approved date");
	}

	@Test
	void aStatusHasNoDatesOfLaterStages() {
		assertCode(BacklinkStatus.PROSPECTED, dates(OCT_1, null, null, null, null), null, "DATE_NOT_ALLOWED");
		assertCode(BacklinkStatus.APPROVED, dates(OCT_1, OCT_3, OCT_6, null, null), LINK, "DATE_NOT_ALLOWED");
		assertCode(BacklinkStatus.LIVE, dates(OCT_1, null, OCT_3, null, OCT_6), LINK, "DATE_NOT_ALLOWED");
		assertCode(BacklinkStatus.REJECTED, dates(OCT_1, null, OCT_3, OCT_6, null), null, "DATE_NOT_ALLOWED");
	}

	@Test
	void stagesHappenInOrderAndNotInTheFuture() {
		assertCode(BacklinkStatus.APPROVED, dates(OCT_3, OCT_1, null, null, null), null, "DATE_ORDER");
		assertCode(BacklinkStatus.LIVE, dates(OCT_1, OCT_6, OCT_3, null, null), LINK, "DATE_ORDER");
		assertCode(BacklinkStatus.LOST, dates(OCT_1, null, OCT_6, null, OCT_3), LINK, "DATE_ORDER");
		assertCode(BacklinkStatus.REJECTED, dates(OCT_3, null, null, OCT_1, null), null, "DATE_ORDER");
		assertCode(BacklinkStatus.SUBMITTED, dates(TODAY.plusDays(1), null, null, null, null), null, "FUTURE_DATE");
	}

	@Test
	void aLiveLinkNeedsItsUrl() {
		assertCode(BacklinkStatus.LIVE, dates(OCT_1, null, OCT_3, null, null), null, "LINK_URL_REQUIRED");
		assertOk(BacklinkStatus.SUBMITTED, dates(OCT_1, null, null, null, null), null);
	}

	@Test
	void movingDatesTheNewStagesAndClearsTheOnesNoLongerAllowed() {
		BacklinkDates submitted = dates(OCT_1, null, null, null, null);
		assertThat(BacklinkRules.moveTo(BacklinkStatus.LIVE, submitted, OCT_6)).isEqualTo(dates(OCT_1, null, OCT_6, null, null));
		assertThat(BacklinkRules.moveTo(BacklinkStatus.APPROVED, submitted, OCT_3)).isEqualTo(dates(OCT_1, OCT_3, null, null, null));
		// Straight from prospect to live: submitted and live on the same day.
		assertThat(BacklinkRules.moveTo(BacklinkStatus.LIVE, BacklinkDates.NONE, OCT_6))
			.isEqualTo(dates(OCT_6, null, OCT_6, null, null));
		BacklinkDates live = dates(OCT_1, OCT_3, OCT_6, null, null);
		assertThat(BacklinkRules.moveTo(BacklinkStatus.LOST, live, TODAY)).isEqualTo(dates(OCT_1, OCT_3, OCT_6, null, TODAY));
		// Back to approved: the live date goes, the earlier stages stay.
		assertThat(BacklinkRules.moveTo(BacklinkStatus.APPROVED, live, TODAY)).isEqualTo(dates(OCT_1, OCT_3, null, null, null));
		assertThat(BacklinkRules.moveTo(BacklinkStatus.PROSPECTED, live, TODAY)).isEqualTo(BacklinkDates.NONE);
	}

	@Test
	void theReferringDomainIsTheHostWithoutWww() {
		assertThat(BacklinkRules.domainOf("https://www.DZone.com/articles/x")).isEqualTo("dzone.com");
		assertThat(BacklinkRules.domainOf("techbullion.com")).isEqualTo("techbullion.com");
		assertThat(BacklinkRules.domainOf("blog.example.co.in/post")).isEqualTo("blog.example.co.in");
		assertThat(BacklinkRules.domainOf("not a domain")).isNull();
		assertThat(BacklinkRules.domainOf("localhost")).isNull();
		assertThat(BacklinkRules.domainOf("  ")).isNull();
	}

	private static BacklinkDates dates(LocalDate s, LocalDate a, LocalDate l, LocalDate r, LocalDate x) {
		return new BacklinkDates(s, a, l, r, x);
	}

	private static void assertOk(BacklinkStatus status, BacklinkDates dates, String linkUrl) {
		assertThatCode(() -> BacklinkRules.check(status, dates, linkUrl, TODAY)).doesNotThrowAnyException();
	}

	private static void assertCode(BacklinkStatus status, BacklinkDates dates, String linkUrl, String code) {
		assertThatThrownBy(() -> BacklinkRules.check(status, dates, linkUrl, TODAY)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo(code);
	}

}
