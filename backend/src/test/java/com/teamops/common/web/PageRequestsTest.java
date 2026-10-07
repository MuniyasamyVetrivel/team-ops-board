package com.teamops.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.teamops.common.exception.ApiException;

class PageRequestsTest {

	private static final Map<String, List<String>> FIELDS = Map.of("name", List.of("firstName", "lastName"),
			"email", List.of("email"));

	@Test
	void usesDefaultSortWhenNoneGiven() {
		Pageable pageable = PageRequests.of(0, 20, null, FIELDS, "name,asc");

		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName")));
	}

	@Test
	void mapsPublicSortKeyToEntityPathsWithDirection() {
		Pageable pageable = PageRequests.of(2, 10, "email,DESC", FIELDS, "name,asc");

		assertThat(pageable.getPageNumber()).isEqualTo(2);
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("email")));
	}

	@Test
	void clampsPageAndSize() {
		assertThat(PageRequests.of(-3, 5000, null, FIELDS, "name").getPageSize()).isEqualTo(PageRequests.MAX_SIZE);
		assertThat(PageRequests.of(-3, 0, null, FIELDS, "name").getPageSize()).isEqualTo(PageRequests.DEFAULT_SIZE);
		assertThat(PageRequests.of(-3, 10, null, FIELDS, "name").getPageNumber()).isZero();
	}

	@Test
	void rejectsUnknownFieldsAndDirections() {
		assertThatThrownBy(() -> PageRequests.of(0, 10, "passwordHash,asc", FIELDS, "name"))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_SORT"));
		assertThatThrownBy(() -> PageRequests.of(0, 10, "name,sideways", FIELDS, "name"))
			.isInstanceOf(ApiException.class);
	}

}
