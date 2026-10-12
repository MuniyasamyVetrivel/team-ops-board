package com.teamops.celebration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.celebration.entity.DayRecurrence;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.ImportantDayCategory;
import com.teamops.celebration.entity.MarketingEvent;
import com.teamops.celebration.entity.MarketingEventStatus;
import com.teamops.celebration.entity.MarketingEventType;
import com.teamops.celebration.entity.OccasionCategory;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.entity.Region;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.celebration.repository.ImportantDayRepository;
import com.teamops.celebration.repository.MarketingEventRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.support.TestFixtures;
import com.teamops.user.entity.User;

class OccasionFinderTest {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);

	private static final LocalDate TO = LocalDate.of(2026, 10, 31);

	private final EmployeeRepository employees = mock(EmployeeRepository.class);

	private final ImportantDayRepository days = mock(ImportantDayRepository.class);

	private final MarketingEventRepository events = mock(MarketingEventRepository.class);

	private OccasionFinder finder;

	@BeforeEach
	void setUp() {
		finder = new OccasionFinder(employees, days, events,
				new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata"),
				"Tritusa");
	}

	@Test
	void birthdaysAndAnniversariesComeFromTheDatesEveryYear() {
		Employee priya = employee(1L, "Priya", "Menon", LocalDate.of(1992, 10, 14), LocalDate.of(2021, 10, 20));
		Employee meera = employee(2L, "Meera", "Iyer", LocalDate.of(1995, 11, 2), null);
		when(employees.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(priya, meera));

		List<Occasion> occasions = finder.between(FROM, TO, false);

		assertThat(occasions).extracting(Occasion::kind, Occasion::date, Occasion::title, Occasion::detail, Occasion::years)
			.containsExactly(
					tuple(OccasionKind.BIRTHDAY, LocalDate.of(2026, 10, 14), "Priya Menon's birthday", "Marketing", null),
					tuple(OccasionKind.WORK_ANNIVERSARY, LocalDate.of(2026, 10, 20), "Priya Menon's work anniversary",
							"5 years at Tritusa", 5));
		assertThat(occasions.get(0).key()).isEqualTo("BIRTHDAY-1-2026-10-14");
		assertThat(finder.yearsAt(1)).isEqualTo("1 year at Tritusa");
	}

	@Test
	void yearlyDaysRepeatAndOneOffDaysDoNot() {
		ImportantDay christmas = day(5L, "Christmas", LocalDate.of(2020, 10, 25), DayRecurrence.YEARLY);
		ImportantDay diwali = day(6L, "Diwali", LocalDate.of(2026, 10, 20), DayRecurrence.ONE_OFF);
		when(days.findCandidates(FROM, TO)).thenReturn(List.of(christmas, diwali));

		assertThat(finder.between(FROM, TO, false)).extracting(Occasion::subjectId, Occasion::date, Occasion::category)
			.containsExactly(tuple(6L, LocalDate.of(2026, 10, 20), OccasionCategory.IMPORTANT_DAY),
					tuple(5L, LocalDate.of(2026, 10, 25), OccasionCategory.IMPORTANT_DAY));
	}

	@Test
	void cancelledEventsOnlyWhenAsked() {
		MarketingEvent webinar = event(7L, MarketingEventType.WEBINAR, MarketingEventStatus.CONFIRMED);
		MarketingEvent cancelled = event(8L, MarketingEventType.EVENT_MARKETING, MarketingEventStatus.CANCELLED);
		when(events.findStartingBetween(any(), any())).thenReturn(List.of(webinar, cancelled));

		assertThat(finder.between(FROM, TO, false)).extracting(Occasion::subjectId, Occasion::category)
			.containsExactly(tuple(7L, OccasionCategory.WEBINAR));
		assertThat(finder.between(FROM, TO, true)).extracting(Occasion::subjectId).containsExactly(7L, 8L);
		assertThat(finder.between(FROM, TO, false).get(0).date()).as("business-zone date").isEqualTo(LocalDate.of(2026, 10, 16));
	}

	@Test
	void eventsThatEndedBeforeTheRangeAreLeftOut() {
		MarketingEvent earlier = event(9L, MarketingEventType.UPCOMING_EVENT, MarketingEventStatus.COMPLETED);
		earlier.setStartAt(Instant.parse("2026-09-28T04:00:00Z"));
		earlier.setEndAt(Instant.parse("2026-09-29T04:00:00Z"));
		MarketingEvent running = event(10L, MarketingEventType.UPCOMING_EVENT, MarketingEventStatus.CONFIRMED);
		running.setStartAt(Instant.parse("2026-09-29T04:00:00Z"));
		running.setEndAt(Instant.parse("2026-10-02T04:00:00Z"));
		when(events.findStartingBetween(any(), any())).thenReturn(List.of(earlier, running));

		assertThat(finder.between(FROM, TO, false)).extracting(Occasion::subjectId, Occasion::category)
			.containsExactly(tuple(10L, OccasionCategory.EVENT));
	}

	static Employee employee(Long id, String first, String last, LocalDate born, LocalDate joined) {
		Employee employee = new Employee();
		ReflectionTestUtils.setField(employee, "id", id);
		employee.setEmployeeCode("TRI-" + id);
		employee.setFirstName(first);
		employee.setLastName(last);
		employee.setDateOfBirth(born);
		employee.setDateOfJoining(joined);
		employee.setDesignation("Marketing");
		employee.setReportingManager("Rakesh");
		return employee;
	}

	static ImportantDay day(Long id, String name, LocalDate date, DayRecurrence recurrence) {
		ImportantDay day = new ImportantDay();
		ReflectionTestUtils.setField(day, "id", id);
		day.setName(name);
		day.setDate(date);
		day.setRecurrence(recurrence);
		day.setCategory(ImportantDayCategory.FESTIVAL);
		day.setRegion(Region.INDIA);
		return day;
	}

	static MarketingEvent event(Long id, MarketingEventType type, MarketingEventStatus status) {
		MarketingEvent event = new MarketingEvent();
		ReflectionTestUtils.setField(event, "id", id);
		event.setTitle("Event " + id);
		event.setEventType(type);
		event.setStatus(status);
		// 20:00 UTC is 01:30 the next day in India.
		event.setStartAt(Instant.parse("2026-10-15T20:00:00Z").plusSeconds(id));
		User owner = TestFixtures.user(3L, "priya.menon@teamops.local", TestFixtures.role(2L, "DEPARTMENT_MANAGER"));
		event.setOwner(owner);
		return event;
	}

}
