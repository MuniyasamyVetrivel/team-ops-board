package com.teamops.devdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.teamops.devdata.DevDataSeeder.SeedUser;

/** Guards the seed list itself: it must stay consistent as people are added. */
class DevDataSeederTest {

	/** Department codes seeded by V2__reference_data.sql. */
	private static final Set<String> V2_DEPARTMENTS = Set.of("IT", "CYBERSEC", "HR", "TA", "WEBDEV", "APPDEV", "DM",
			"PRESALES", "GRAPHICS", "PAYROLL");

	@Test
	void everyDepartmentHasAManagerAndAnEmployee() {
		assertThat(DevDataSeeder.departmentCodes()).isEqualTo(V2_DEPARTMENTS);
		for (String code : V2_DEPARTMENTS) {
			assertThat(DevDataSeeder.USERS).as("manager for " + code)
				.anyMatch(u -> u.departmentCode().equals(code) && u.leadsDepartment());
			assertThat(DevDataSeeder.USERS).as("employee for " + code)
				.anyMatch(u -> u.departmentCode().equals(code) && u.role().equals("EMPLOYEE"));
		}
	}

	@Test
	void reportsToTargetsAreSeededEarlierInTheList() {
		Set<String> seen = new HashSet<>();
		for (SeedUser user : DevDataSeeder.USERS) {
			if (user.reportsToEmail() != null) {
				assertThat(seen).as(user.email() + " reports to someone seeded later").contains(user.reportsToEmail());
			}
			assertThat(seen.add(user.email())).as("duplicate seed email " + user.email()).isTrue();
		}
	}

	@Test
	void exactlyOneManagerPerDepartment() {
		Set<String> led = new HashSet<>();
		DevDataSeeder.USERS.stream()
			.filter(SeedUser::leadsDepartment)
			.forEach(u -> assertThat(led.add(u.departmentCode())).as("two leads for " + u.departmentCode()).isTrue());
	}

	@Test
	void seedTicketsReferenceSeededPeople() {
		Set<String> emails = new HashSet<>();
		DevDataSeeder.USERS.forEach(u -> emails.add(u.email()));
		for (DevTicketSeeder.Seed ticket : DevTicketSeeder.TICKETS) {
			assertThat(emails).as("requester of " + ticket.subject()).contains(ticket.requester());
			if (ticket.assignee() != null) {
				assertThat(emails).as("assignee of " + ticket.subject()).contains(ticket.assignee());
			}
			assertThat(ticket.status().isDone() ? ticket.doneAfter() : 0).as("resolution time of " + ticket.subject())
				.isNotNull();
		}
	}

}
