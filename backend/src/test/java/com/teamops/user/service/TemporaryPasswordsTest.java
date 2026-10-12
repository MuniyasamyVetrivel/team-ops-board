package com.teamops.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.common.security.PasswordPolicy;

class TemporaryPasswordsTest {

	@Test
	void passwordsMeetThePolicyAndAvoidLookAlikes() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			String password = TemporaryPasswords.generate();
			assertThat(password).hasSize(TemporaryPasswords.LENGTH).doesNotContainPattern("[0O1lI]");
			assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
			seen.add(password);
		}
		assertThat(seen).hasSize(500);
	}

	@Test
	void codeListsAcceptCommasSemicolonsAndSpaces() {
		assertThat(UserCsvImporter.codes(" marketing_view, LEAD_EDIT;seo_view  lead_edit ")).containsExactly(
				"MARKETING_VIEW", "LEAD_EDIT", "SEO_VIEW");
		assertThat(UserCsvImporter.codes(null)).isEmpty();
	}

}
