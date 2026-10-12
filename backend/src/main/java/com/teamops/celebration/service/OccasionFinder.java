package com.teamops.celebration.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.MarketingEvent;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.celebration.repository.ImportantDayRepository;
import com.teamops.celebration.repository.MarketingEventRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;

/**
 * Computes the birthdays and work anniversaries of active employees, the important days and the marketing events in a
 * date range. Nothing is stored per occurrence: a birthday is simply the date of birth's month and day in each year.
 */
@Component
@Transactional(readOnly = true)
public class OccasionFinder {

	/** Longest an event is expected to run: earlier starts are not looked for. */
	static final int EVENT_LOOKBACK_DAYS = 31;

	static final Comparator<Occasion> ORDER = Comparator.comparing(Occasion::date)
		.thenComparing(o -> o.startAt() == null ? Instant.MIN : o.startAt())
		.thenComparing(Occasion::kind)
		.thenComparing(Occasion::title)
		.thenComparing(Occasion::subjectId);

	private final EmployeeRepository employeeRepository;

	private final ImportantDayRepository importantDayRepository;

	private final MarketingEventRepository eventRepository;

	private final BusinessCalendar calendar;

	private final String companyName;

	public OccasionFinder(EmployeeRepository employeeRepository, ImportantDayRepository importantDayRepository,
			MarketingEventRepository eventRepository, BusinessCalendar calendar,
			@Value("${app.celebrations.company-name:Tritusa}") String companyName) {
		this.employeeRepository = employeeRepository;
		this.importantDayRepository = importantDayRepository;
		this.eventRepository = eventRepository;
		this.calendar = calendar;
		this.companyName = companyName;
	}

	/**
	 * Occurrences in [from, to] (inclusive), by date. Events are included while they overlap the range, so an event
	 * that started earlier and is still running shows too.
	 * @param includeCancelled whether cancelled events are included (the calendar marks them; reminders skip them)
	 */
	public List<Occasion> between(LocalDate from, LocalDate to, boolean includeCancelled) {
		List<Occasion> occasions = new ArrayList<>();
		for (Employee employee : employeeRepository.findByStatus(EmployeeStatus.ACTIVE)) {
			addCelebrations(employee, from, to, occasions);
		}
		for (ImportantDay day : importantDayRepository.findCandidates(from, to)) {
			List<LocalDate> dates = day.isYearly() ? AnnualDates.between(day.getDate(), day.getDate().getYear(), from, to)
					: List.of(day.getDate());
			dates.forEach(date -> occasions.add(importantDay(day, date)));
		}
		// Events that start in the range, plus a few days back for multi-day events still running.
		for (MarketingEvent event : eventRepository.findStartingBetween(calendar.startOf(from.minusDays(EVENT_LOOKBACK_DAYS)),
				calendar.startOf(to.plusDays(1)))) {
			Occasion occasion = event(event);
			if ((includeCancelled || !event.isCancelled()) && !occasion.endDate().isBefore(from)) {
				occasions.add(occasion);
			}
		}
		occasions.sort(ORDER);
		return occasions;
	}

	/** The occasion an event is on the calendar as (its start date). */
	public Occasion event(MarketingEvent event) {
		LocalDate start = LocalDate.ofInstant(event.getStartAt(), calendar.zone());
		LocalDate end = event.getEndAt() == null ? start : LocalDate.ofInstant(event.getEndAt(), calendar.zone());
		return new Occasion(OccasionKind.EVENT, event.getId(), start, end, event.getTitle(), event.getTitle(),
				event.getLocation(), null, event.getStartAt(), event.getEndAt(), null, UserSummary.of(event.getOwner()),
				null, null, event.getEventType(), event.getStatus(), event.getLocation(),
				event.getEmailCampaign() == null ? null : event.getEmailCampaign().getId());
	}

	public Occasion importantDay(ImportantDay day, LocalDate date) {
		return new Occasion(OccasionKind.IMPORTANT_DAY, day.getId(), date, date, day.getName(), day.getName(),
				day.getRegion().label(), null, null, null, null, null, day.getCategory(), day.getRegion(), null, null,
				null, null);
	}

	public Occasion birthday(Employee employee, LocalDate date) {
		return new Occasion(OccasionKind.BIRTHDAY, employee.getId(), date, date, employee.getFullName(),
				employee.getFullName() + "'s birthday", employee.getDesignation(), null, null, null,
				department(employee), UserSummary.of(employee.getUser()), null, null, null, null, null, null);
	}

	public Occasion anniversary(Employee employee, LocalDate date) {
		int years = AnnualDates.years(employee.getDateOfJoining(), date);
		return new Occasion(OccasionKind.WORK_ANNIVERSARY, employee.getId(), date, date, employee.getFullName(),
				employee.getFullName() + "'s work anniversary", yearsAt(years), years, null, null, department(employee),
				UserSummary.of(employee.getUser()), null, null, null, null, null, null);
	}

	/** "1 year at Tritusa", "5 years at Tritusa". */
	public String yearsAt(int years) {
		return years + (years == 1 ? " year at " : " years at ") + companyName;
	}

	private void addCelebrations(Employee employee, LocalDate from, LocalDate to, List<Occasion> occasions) {
		LocalDate born = employee.getDateOfBirth();
		AnnualDates.between(born, AnnualDates.firstBirthdayYear(born), from, to)
			.forEach(date -> occasions.add(birthday(employee, date)));
		LocalDate joined = employee.getDateOfJoining();
		if (joined != null) {
			AnnualDates.between(joined, AnnualDates.firstAnniversaryYear(joined), from, to)
				.forEach(date -> occasions.add(anniversary(employee, date)));
		}
	}

	private static DepartmentSummary department(Employee employee) {
		return employee.getDepartment() == null ? null : DepartmentSummary.of(employee.getDepartment());
	}

}
