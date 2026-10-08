package com.teamops.marketing.seo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.teamops.common.exception.ApiException;

/** Sorting of the SEO ranking table is whitelisted; no client text reaches the SQL. */
class SeoRankingTableQueryTest {

	@Test
	void bestIsTheDefaultAndPutsMissingRankingsLast() {
		assertThat(SeoRankingTableQuery.orderBy(null)).startsWith("(cur.id is null), (cur.ranking_position is null)");
		assertThat(SeoRankingTableQuery.orderBy("best,desc")).isEqualTo(SeoRankingTableQuery.orderBy("best"));
	}

	@Test
	void directionalSortsTakeTheDirection() {
		assertThat(SeoRankingTableQuery.orderBy("volume,desc")).contains("k.search_volume desc");
		assertThat(SeoRankingTableQuery.orderBy("keyword")).startsWith("k.keyword asc");
	}

	@Test
	void unknownSortsAndDirectionsAreRejected() {
		assertThatThrownBy(() -> SeoRankingTableQuery.orderBy("k.id; drop table users")).isInstanceOf(ApiException.class)
			.extracting("code")
			.isEqualTo("INVALID_SORT");
		assertThatThrownBy(() -> SeoRankingTableQuery.orderBy("page,sideways")).isInstanceOf(ApiException.class);
	}

}
