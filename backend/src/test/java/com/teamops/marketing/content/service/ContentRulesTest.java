package com.teamops.marketing.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;
import com.teamops.marketing.content.entity.ContentStatus;
import com.teamops.marketing.content.service.ContentRules.Dates;

class ContentRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

	private static final LocalDate OCT_2 = LocalDate.of(2026, 10, 2);

	private static final LocalDate OCT_6 = LocalDate.of(2026, 10, 6);

	private static final String URL = "https://www.example.com/blog/sap-testing";

	@Test
	void briefSection48RemainingIsTargetLessPublished() {
		assertThat(ContentRules.remaining(new BigDecimal("12"), 9)).isEqualByComparingTo("3");
		assertThat(ContentRules.remaining(new BigDecimal("12"), 14)).isEqualByComparingTo("0");
		assertThat(ContentRules.remaining(null, 9)).isNull();
	}

	@Test
	void liveContentHasItsUrlAndPublicationDate() {
		assertOk(ContentStatus.PUBLISHED, new Dates(OCT_2, null), URL);
		assertOk(ContentStatus.UPDATED, new Dates(OCT_2, OCT_6), URL);
		assertCode(ContentStatus.PUBLISHED, Dates.NONE, URL, "MISSING_DATE");
		assertCode(ContentStatus.PUBLISHED, new Dates(OCT_2, null), null, "CONTENT_URL_REQUIRED");
		assertCode(ContentStatus.UPDATED, new Dates(OCT_2, null), URL, "MISSING_DATE");
		assertCode(ContentStatus.PUBLISHED, new Dates(OCT_2, OCT_6), URL, "DATE_NOT_ALLOWED");
	}

	@Test
	void contentThatIsNotLiveHasNoPublicationDate() {
		for (ContentStatus status : new ContentStatus[] { ContentStatus.IDEA, ContentStatus.PLANNED,
				ContentStatus.IN_PROGRESS, ContentStatus.DRAFT }) {
			assertOk(status, Dates.NONE, null);
			assertCode(status, new Dates(OCT_2, null), URL, "DATE_NOT_ALLOWED");
		}
	}

	@Test
	void datesAreNotInTheFutureAndARefreshComesAfterPublication() {
		assertCode(ContentStatus.PUBLISHED, new Dates(TODAY.plusDays(1), null), URL, "FUTURE_DATE");
		assertCode(ContentStatus.UPDATED, new Dates(OCT_2, TODAY.plusDays(1)), URL, "FUTURE_DATE");
		assertCode(ContentStatus.UPDATED, new Dates(OCT_6, OCT_2), URL, "DATE_ORDER");
	}

	@Test
	void movingDatesPublicationAndRefreshAndClearsThemBeforePublishing() {
		assertThat(ContentRules.moveTo(ContentStatus.PUBLISHED, Dates.NONE, OCT_6)).isEqualTo(new Dates(OCT_6, null));
		// An item that is already published keeps its publication date.
		assertThat(ContentRules.moveTo(ContentStatus.UPDATED, new Dates(OCT_2, null), OCT_6)).isEqualTo(new Dates(OCT_2, OCT_6));
		assertThat(ContentRules.moveTo(ContentStatus.PUBLISHED, new Dates(OCT_2, OCT_6), TODAY)).isEqualTo(new Dates(OCT_2, null));
		assertThat(ContentRules.moveTo(ContentStatus.DRAFT, new Dates(OCT_2, OCT_6), TODAY)).isEqualTo(Dates.NONE);
	}

	private static void assertOk(ContentStatus status, Dates dates, String url) {
		assertThatCode(() -> ContentRules.check(status, dates, url, TODAY)).doesNotThrowAnyException();
	}

	private static void assertCode(ContentStatus status, Dates dates, String url, String code) {
		assertThatThrownBy(() -> ContentRules.check(status, dates, url, TODAY)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo(code);
	}

}
