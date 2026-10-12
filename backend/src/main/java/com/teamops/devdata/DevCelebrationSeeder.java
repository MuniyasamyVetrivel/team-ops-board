package com.teamops.devdata;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.celebration.entity.DayRecurrence;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.ImportantDayCategory;
import com.teamops.celebration.entity.LinkedInPost;
import com.teamops.celebration.entity.LinkedInPostStatus;
import com.teamops.celebration.entity.MarketingEvent;
import com.teamops.celebration.entity.MarketingEventStatus;
import com.teamops.celebration.entity.MarketingEventType;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.entity.Region;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.celebration.repository.ImportantDayRepository;
import com.teamops.celebration.repository.LinkedInPostRepository;
import com.teamops.celebration.repository.MarketingEventRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Employees with birthdays and work anniversaries spread over the next two months (one birthday and one anniversary
 * two days out, to see reminders), the brief's important days, two webinars and an event. Dates are relative to today.
 * Idempotent: each record is only created when its employee ID, name and date, or title is not there yet.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevCelebrationSeeder {

	/** Diwali moves every year, so it is entered per year (a one-off important day). */
	static final List<LocalDate> DIWALI = List.of(LocalDate.of(2026, 11, 8), LocalDate.of(2027, 10, 29),
			LocalDate.of(2028, 10, 17));

	private static final String PRIYA = "priya.menon@teamops.local";

	private final EmployeeRepository employeeRepository;

	private final ImportantDayRepository dayRepository;

	private final MarketingEventRepository eventRepository;

	private final LinkedInPostRepository postRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final JdbcTemplate jdbc;

	private final BusinessCalendar calendar;

	/**
	 * @param birthdayIn days from today to the next birthday
	 * @param anniversaryIn days from today to the next work anniversary, with {@code years} completed; null = no
	 * joining date
	 */
	record SeedEmployee(String code, String firstName, String lastName, int age, int birthdayIn, Integer anniversaryIn,
			int years, String designation, String manager, String departmentCode, String office, String userEmail,
			EmployeeStatus status) {
	}

	static final List<SeedEmployee> EMPLOYEES = List.of(
			new SeedEmployee("TRI-0101", "Priya", "Menon", 34, 2, 9, 5, "Digital Marketing Manager", "Rakesh", "DM", "Chennai", PRIYA, EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0102", "Arun", "Kumar", 27, 11, 2, 3, "SEO Executive", "Priya Menon", "DM", "Chennai", "arun.kumar@teamops.local", EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0103", "Kavya", "Suresh", 26, 25, 40, 2, "Content Writer", "Priya Menon", "DM", "Chennai", "kavya.suresh@teamops.local", EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0104", "Meera", "Iyer", 31, 5, null, 0, "Content Strategist (Consultant)", "Priya Menon", "DM", "Bengaluru", null, EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0105", "Daniel", "Hughes", 42, 18, 30, 7, "Account Director", "Rakesh", "PRESALES", "Sydney", null, EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0106", "Sophie", "Turner", 29, 33, 14, 1, "Business Development Manager", "Daniel Hughes", "PRESALES", "Melbourne", null, EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0107", "Rahul", "Verma", 30, 41, 52, 4, "QA Engineer", "Anjali Nair", null, "Chennai", null, EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0108", "Anjali", "Nair", 36, 48, 6, 6, "Test Lead", "Rakesh", null, "Chennai", null, EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0109", "Liam", "O'Connor", 38, 56, null, 0, "Cloud Consultant (Contract)", "Daniel Hughes", null, "Sydney", null, EmployeeStatus.ACTIVE),
			new SeedEmployee("TRI-0110", "Fatima", "Sheikh", 45, 60, 21, 10, "HR Business Partner", "Rakesh", "HR", "Chennai", null, EmployeeStatus.ACTIVE),
			// Left the company: on record, but never on the calendar or in reminders.
			new SeedEmployee("TRI-0090", "Ravi", "Shankar", 33, 3, 4, 2, "Former QA Analyst", "Anjali Nair", null, "Chennai", null, EmployeeStatus.LEFT));

	/** @return a short summary for the log */
	@Transactional
	public String seed() {
		int granted = jdbc.update("""
				insert ignore into user_permissions (user_id, permission_id)
				select u.id, p.id from users u join permissions p on p.code = 'CELEBRATIONS_EDIT'
				where u.email = ?
				""", PRIYA);
		LocalDate today = calendar.today();
		User rakesh = userRepository.findByEmailIgnoreCase("rakesh@teamops.local").orElse(null);
		int employees = seedEmployees(today, rakesh);
		int days = seedImportantDays(today, rakesh);
		int events = seedEvents(today, rakesh);
		return "%d employees, %d important days and %d events for the calendar%s".formatted(employees, days, events,
				granted > 0 ? "; CELEBRATIONS_EDIT granted to Priya Menon" : "");
	}

	private int seedEmployees(LocalDate today, User createdBy) {
		int created = 0;
		for (SeedEmployee seed : EMPLOYEES) {
			if (employeeRepository.existsByEmployeeCode(seed.code())) {
				continue;
			}
			LocalDate birthday = today.plusDays(seed.birthdayIn());
			Employee employee = new Employee();
			employee.setEmployeeCode(seed.code());
			employee.setFirstName(seed.firstName());
			employee.setLastName(seed.lastName());
			employee.setDateOfBirth(birthday.minusYears(seed.age()));
			employee.setDesignation(seed.designation());
			employee.setReportingManager(seed.manager());
			employee.setDateOfJoining(seed.anniversaryIn() == null ? null
					: today.plusDays(seed.anniversaryIn()).minusYears(seed.years()));
			employee.setDepartment(seed.departmentCode() == null ? null
					: departmentRepository.findByCode(seed.departmentCode()).orElse(null));
			employee.setOfficeLocation(seed.office());
			employee.setWorkEmail((seed.firstName() + "." + seed.lastName()).toLowerCase(Locale.ROOT).replace("'", "") + "@tritusa.example");
			if (seed.userEmail() != null) {
				userRepository.findByEmailIgnoreCase(seed.userEmail())
					.filter(user -> employeeRepository.findByUserId(user.getId()).isEmpty())
					.ifPresent(employee::setUser);
			}
			employee.setStatus(seed.status());
			employee.setCreatedBy(createdBy);
			employeeRepository.save(employee);
			created++;
		}
		return created;
	}

	private int seedImportantDays(LocalDate today, User createdBy) {
		int year = today.getYear();
		int created = 0;
		LocalDate diwali = DIWALI.stream().filter(date -> !date.isBefore(today)).findFirst().orElse(null);
		if (diwali != null) {
			created += day("Diwali", diwali, ImportantDayCategory.FESTIVAL, Region.INDIA, DayRecurrence.ONE_OFF,
					"Festival of lights.", "Greeting creative for India and a LinkedIn post. The date changes every year.",
					createdBy);
		}
		LocalDate qualityDay = LocalDate.of(year, Month.NOVEMBER, 1)
			.with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.THURSDAY));
		if (qualityDay.isBefore(today)) {
			qualityDay = LocalDate.of(year + 1, Month.NOVEMBER, 1)
				.with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.THURSDAY));
		}
		created += day("World Quality Day", qualityDay, ImportantDayCategory.INDUSTRY_DAY, Region.GLOBAL,
				DayRecurrence.ONE_OFF, "Second Thursday of November.",
				"Thought-leadership post on quality engineering; link the testing services page.", createdBy);
		created += day("Australia Day", LocalDate.of(year, Month.JANUARY, 26), ImportantDayCategory.NATIONAL_DAY,
				Region.AUSTRALIA, DayRecurrence.YEARLY, null, "Greeting for the Australian audience.", createdBy);
		created += day("ANZAC Day", LocalDate.of(year, Month.APRIL, 25), ImportantDayCategory.NATIONAL_DAY,
				Region.AUSTRALIA, DayRecurrence.YEARLY, null, "Respectful, low-key message only.", createdBy);
		created += day("Independence Day (India)", LocalDate.of(year, Month.AUGUST, 15),
				ImportantDayCategory.NATIONAL_DAY, Region.INDIA, DayRecurrence.YEARLY, null,
				"Greeting creative for India.", createdBy);
		created += day("Christmas", LocalDate.of(year, Month.DECEMBER, 25), ImportantDayCategory.FESTIVAL, Region.GLOBAL,
				DayRecurrence.YEARLY, null, "Season's greetings email and LinkedIn post.", createdBy);
		return created;
	}

	private int day(String name, LocalDate date, ImportantDayCategory category, Region region, DayRecurrence recurrence,
			String description, String notes, User createdBy) {
		boolean exists = recurrence == DayRecurrence.YEARLY
				? dayRepository.existsByNameAndRegionAndRecurrence(name, region, recurrence)
				: dayRepository.existsByNameAndDateAndRegion(name, date, region);
		if (exists) {
			return 0;
		}
		ImportantDay day = new ImportantDay();
		day.setName(name);
		day.setDate(date);
		day.setCategory(category);
		day.setRegion(region);
		day.setRecurrence(recurrence);
		day.setDescription(description);
		day.setMarketingNotes(notes);
		day.setCreatedBy(createdBy);
		dayRepository.save(day);
		return 1;
	}

	private int seedEvents(LocalDate today, User createdBy) {
		User priya = userRepository.findByEmailIgnoreCase(PRIYA).orElse(null);
		User kavya = userRepository.findByEmailIgnoreCase("kavya.suresh@teamops.local").orElse(null);
		if (priya == null) {
			return 0;
		}
		int created = 0;
		MarketingEvent webinar = event("Webinar: Quality engineering for SAP S/4HANA", MarketingEventType.WEBINAR,
				MarketingEventStatus.CONFIRMED, today.plusDays(6), LocalTime.of(15, 0), 60, null,
				"https://www.example.com/webinars/sap-quality", "Live demo of our SAP test automation accelerators.",
				priya, createdBy);
		if (webinar != null) {
			created++;
			if (kavya != null) {
				LinkedInPost post = new LinkedInPost();
				post.setSubjectType(OccasionKind.EVENT);
				post.setSubjectId(webinar.getId());
				post.setOccurrenceDate(today.plusDays(6));
				post.setPostNeeded(true);
				post.setOwner(kavya);
				post.setStatus(LinkedInPostStatus.DRAFTED);
				post.setUpdatedBy(priya);
				postRepository.save(post);
			}
		}
		created += event("Webinar: AI in software testing", MarketingEventType.WEBINAR, MarketingEventStatus.PLANNED,
				today.plusDays(27), LocalTime.of(11, 0), 45, null, "https://www.example.com/webinars/ai-testing",
				"Panel with our test architects.", priya, createdBy) == null ? 0 : 1;
		created += event("Tritusa at the Sydney Testing Summit", MarketingEventType.UPCOMING_EVENT,
				MarketingEventStatus.PLANNED, today.plusDays(16), LocalTime.of(9, 0), 8 * 60, "ICC Sydney", null,
				"Booth and a 20-minute talk on test automation.", priya, createdBy) == null ? 0 : 1;
		return created;
	}

	/** @return the new event, or null when one with this title exists */
	private MarketingEvent event(String title, MarketingEventType type, MarketingEventStatus status, LocalDate day,
			LocalTime start, int minutes, String location, String registrationLink, String description, User owner,
			User createdBy) {
		if (eventRepository.existsByTitle(title)) {
			return null;
		}
		MarketingEvent event = new MarketingEvent();
		event.setTitle(title);
		event.setEventType(type);
		event.setStatus(status);
		event.setStartAt(day.atTime(start).atZone(calendar.zone()).toInstant());
		event.setEndAt(event.getStartAt().plusSeconds(minutes * 60L));
		event.setLocation(location);
		event.setOnlineLink(location == null ? "https://www.example.com/live" : null);
		event.setRegistrationLink(registrationLink);
		event.setDescription(description);
		event.setOwner(owner);
		event.setCreatedBy(createdBy);
		return eventRepository.save(event);
	}

}
