package com.teamops.bench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.common.exception.ApiException;

/** The computed statuses of consultants and engagements, and how skills are cleaned up. */
class BenchRulesTest {

	@Test
	void anActiveConsultantsStatusFollowsTheirAllocation() {
		assertThat(ConsultantStatus.of(EmploymentStatus.ACTIVE, 0)).isEqualTo(ConsultantStatus.ON_BENCH);
		assertThat(ConsultantStatus.of(EmploymentStatus.ACTIVE, 1)).isEqualTo(ConsultantStatus.PARTIALLY_ALLOCATED);
		assertThat(ConsultantStatus.of(EmploymentStatus.ACTIVE, 99)).isEqualTo(ConsultantStatus.PARTIALLY_ALLOCATED);
		assertThat(ConsultantStatus.of(EmploymentStatus.ACTIVE, 100)).isEqualTo(ConsultantStatus.ALLOCATED);
		assertThat(ConsultantStatus.of(EmploymentStatus.ACTIVE, 120)).isEqualTo(ConsultantStatus.ALLOCATED);
	}

	@Test
	void hrsEmploymentStatusWinsOverAllocations() {
		for (int percent : new int[] { 0, 50, 100 }) {
			assertThat(ConsultantStatus.of(EmploymentStatus.LEAVE_OF_ABSENCE, percent))
				.isEqualTo(ConsultantStatus.LEAVE_OF_ABSENCE);
			assertThat(ConsultantStatus.of(EmploymentStatus.NOTICE_PERIOD, percent))
				.isEqualTo(ConsultantStatus.NOTICE_PERIOD);
			assertThat(ConsultantStatus.of(EmploymentStatus.LEFT, percent)).isEqualTo(ConsultantStatus.LEFT);
		}
		for (ConsultantStatus status : ConsultantStatus.values()) {
			EmploymentStatus employment = status.employmentStatus();
			if (employment != null) {
				assertThat(ConsultantStatus.of(employment, 0)).isEqualTo(status);
			}
		}
	}

	@Test
	void engagementStatusFollowsTheDatesWithBothDaysIncluded() {
		LocalDate start = LocalDate.of(2026, 11, 1);
		LocalDate end = LocalDate.of(2027, 3, 31);
		assertThat(EngagementStatus.of(start, end, LocalDate.of(2026, 10, 31))).isEqualTo(EngagementStatus.UPCOMING);
		assertThat(EngagementStatus.of(start, end, start)).isEqualTo(EngagementStatus.ACTIVE);
		assertThat(EngagementStatus.of(start, end, end)).isEqualTo(EngagementStatus.ACTIVE);
		assertThat(EngagementStatus.of(start, end, end.plusDays(1))).isEqualTo(EngagementStatus.ENDED);
		assertThat(EngagementStatus.of(start, null, LocalDate.of(2040, 1, 1))).isEqualTo(EngagementStatus.ACTIVE);
	}

	@Test
	void skillsAreTrimmedAndRepeatsIgnoringCaseDropped() {
		assertThat(BenchText.skills(List.of(" SAP S/4HANA ", "Tosca", "", "tosca", "Test automation")))
			.containsExactly("SAP S/4HANA", "Tosca", "Test automation");
		assertThat(BenchText.skills(null)).isEmpty();
		assertThat(BenchText.splitList("SAP S/4HANA; Tosca;; tosca ;Performance testing"))
			.containsExactly("SAP S/4HANA", "Tosca", "Performance testing");
		assertThat(BenchText.joinList(List.of("SAP S/4HANA", "Tosca"))).isEqualTo("SAP S/4HANA; Tosca");
	}

	@Test
	void tooManyOrTooLongSkillsAreRejected() {
		List<String> many = new ArrayList<>();
		for (int i = 0; i <= BenchText.MAX_SKILLS; i++) {
			many.add("Skill " + i);
		}
		assertThatThrownBy(() -> BenchText.skills(many)).isInstanceOf(ApiException.class)
			.extracting("code")
			.isEqualTo("TOO_MANY_SKILLS");
		String tooLong = String.join("", Collections.nCopies(BenchText.SKILL_LENGTH + 1, "x"));
		assertThatThrownBy(() -> BenchText.skills(List.of(tooLong))).isInstanceOf(ApiException.class)
			.extracting("code")
			.isEqualTo("SKILL_TOO_LONG");
	}

}
