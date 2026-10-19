package com.teamops.celebration.service;

import java.time.Instant;
import java.time.LocalDate;

import com.teamops.celebration.entity.ImportantDayCategory;
import com.teamops.celebration.entity.MarketingEventStatus;
import com.teamops.celebration.entity.MarketingEventType;
import com.teamops.celebration.entity.OccasionCategory;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.entity.Region;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;

/**
 * One occurrence of a birthday, work anniversary, important day or marketing event, computed for a date range.
 * @param subjectId the employee (birthdays, anniversaries), important day or event
 * @param date the occurrence's business-zone date (an event's start date)
 * @param name what reminders call it: the person, the day or the event title
 * @param title the calendar title, e.g. "Alex Taylor's birthday"
 * @param person the event owner, or the employee's app user
 * @param startAt for events only (timed)
 */
public record Occasion(OccasionKind kind, Long subjectId, LocalDate date, LocalDate endDate, String name, String title,
		String detail, Integer years, Instant startAt, Instant endAt, DepartmentSummary department, UserSummary person,
		ImportantDayCategory dayCategory, Region region, MarketingEventType eventType, MarketingEventStatus eventStatus,
		String location, Long emailCampaignId) {

	public OccasionCategory category() {
		return switch (kind) {
			case BIRTHDAY -> OccasionCategory.BIRTHDAY;
			case WORK_ANNIVERSARY -> OccasionCategory.WORK_ANNIVERSARY;
			case IMPORTANT_DAY -> OccasionCategory.IMPORTANT_DAY;
			case EVENT -> eventType.category();
		};
	}

	/** Unique per occurrence, e.g. "BIRTHDAY-12-2026-10-14". */
	public String key() {
		return key(kind, subjectId, date);
	}

	public static String key(OccasionKind kind, Long subjectId, LocalDate date) {
		return kind + "-" + subjectId + "-" + date;
	}

	public boolean allDay() {
		return startAt == null;
	}

}
