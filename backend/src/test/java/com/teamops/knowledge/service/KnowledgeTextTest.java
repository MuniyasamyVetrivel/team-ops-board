package com.teamops.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeTextTest {

	@Test
	void slugsAreLowercaseAsciiWithSingleHyphens() {
		assertThat(KnowledgeText.slugify("How to reset your VPN password?")).isEqualTo("how-to-reset-your-vpn-password");
		assertThat(KnowledgeText.slugify("  Café  Wi-Fi -- setup!! ")).isEqualTo("cafe-wi-fi-setup");
		assertThat(KnowledgeText.slugify("???")).isEqualTo("article");
		assertThat(KnowledgeText.slugify("a".repeat(300))).hasSize(KnowledgeText.MAX_SLUG);
	}

	@Test
	void fullTextQueriesRequireEveryIndexedWordAsAPrefixAndDropOperators() {
		assertThat(KnowledgeText.booleanQuery("VPN password")).isEqualTo("+vpn* +password*");
		assertThat(KnowledgeText.booleanQuery("reset \"wifi\" -router +(x)")).isEqualTo("+reset* +wifi* +router*");
		assertThat(KnowledgeText.booleanQuery("go to it")).as("all words too short to be indexed").isNull();
		assertThat(KnowledgeText.booleanQuery(null)).isNull();
	}

	@Test
	void excerptsArePlainText() {
		String markdown = """
				# Reset your password

				Open **Settings** and click [Security](https://example.com/security).

				```bash
				secret-command
				```
				Then follow the steps.
				""";

		assertThat(KnowledgeText.excerpt(markdown, 200))
			.isEqualTo("Reset your password Open Settings and click Security. Then follow the steps.");
		assertThat(KnowledgeText.excerpt("word ".repeat(100), 20)).hasSize(20).endsWith("…");
	}

}
