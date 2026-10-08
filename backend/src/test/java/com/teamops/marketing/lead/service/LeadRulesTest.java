package com.teamops.marketing.lead.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.lead.repository.LeadQuery.LinkKind;

/** Brief sections 43–44: which campaign or content each lead source may name. */
class LeadRulesTest {

	@Test
	void eachSourceTakesItsOwnKindOfLink() {
		assertThat(LeadRules.linkFor(LeadSource.EMAIL)).contains(LinkKind.EMAIL_CAMPAIGN);
		assertThat(LeadRules.linkFor(LeadSource.LINKEDIN)).contains(LinkKind.PAID_CAMPAIGN);
		assertThat(LeadRules.linkFor(LeadSource.PAID_CAMPAIGN)).contains(LinkKind.PAID_CAMPAIGN);
		assertThat(LeadRules.linkFor(LeadSource.BLOG)).contains(LinkKind.CONTENT);
		for (LeadSource source : new LeadSource[] { LeadSource.ORGANIC, LeadSource.WEBSITE, LeadSource.REFERRAL,
				LeadSource.OTHER }) {
			assertThat(LeadRules.linkFor(source)).as(source.name()).isEmpty();
		}
	}

	@Test
	void aSourceAcceptsOnlyItsKind() {
		assertThat(LeadRules.accepts(LeadSource.EMAIL, LinkKind.EMAIL_CAMPAIGN)).isTrue();
		assertThat(LeadRules.accepts(LeadSource.EMAIL, LinkKind.PAID_CAMPAIGN)).isFalse();
		assertThat(LeadRules.accepts(LeadSource.LINKEDIN, LinkKind.CONTENT)).isFalse();
		assertThat(LeadRules.accepts(LeadSource.BLOG, LinkKind.CONTENT)).isTrue();
		assertThat(LeadRules.accepts(LeadSource.ORGANIC, LinkKind.CONTENT)).isFalse();
	}

	@Test
	void aLeadCannotComeInBeforeItsCampaignOrContent() {
		LocalDate sent = LocalDate.of(2026, 10, 6);
		assertThat(LeadRules.before(LocalDate.of(2026, 10, 5), sent)).isTrue();
		assertThat(LeadRules.before(sent, sent)).isFalse();
		assertThat(LeadRules.before(LocalDate.of(2026, 10, 9), sent)).isFalse();
		assertThat(LeadRules.before(LocalDate.of(2026, 10, 9), null)).isFalse();
	}

}
