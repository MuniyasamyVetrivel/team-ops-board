package com.teamops.devdata;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.activity.entity.ActivityChecklistItem;
import com.teamops.marketing.activity.entity.ActivityOccurrence;
import com.teamops.marketing.activity.entity.Frequency;
import com.teamops.marketing.activity.entity.MarketingActivity;
import com.teamops.marketing.activity.entity.OccurrenceStatus;
import com.teamops.marketing.activity.repository.MarketingActivityRepository;
import com.teamops.marketing.activity.service.OccurrenceEngine;
import com.teamops.marketing.activity.service.Recurrence;
import com.teamops.task.entity.TaskPriority;
import com.teamops.task.entity.TaskStatus;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Recurring marketing activities from brief sections 35–36. "Monthly SEO Ranking Update" has last month's occurrence
 * completed (which created this month's), so its history shows; the others start with the current period. Runs only
 * while there are no activities.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevActivitySeeder {

	private static final String PRIYA = "priya.menon@teamops.local";

	private static final String ARUN = "arun.kumar@teamops.local";

	private static final String KAVYA = "kavya.suresh@teamops.local";

	private final MarketingActivityRepository activityRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final OccurrenceEngine engine;

	private final BusinessCalendar calendar;

	/** @return the number of activities created (0 when activities already exist) */
	@Transactional
	public int seedIfEmpty() {
		if (activityRepository.count() > 0) {
			return 0;
		}
		LocalDate today = calendar.today();
		LocalDate threeMonthsAgo = today.minusMonths(3).withDayOfMonth(1);
		MarketingActivity rankings = save("Monthly SEO Ranking Update", Frequency.MONTHLY, threeMonthsAgo, 4,
				"Update {month} keyword rankings", ARUN, TaskPriority.HIGH,
				List.of("Export positions from the rank tracker", "Record the month on the SEO Rankings page",
						"Flag keywords that dropped out of the top 10"));
		save("Weekly blog publishing", Frequency.WEEKLY, threeMonthsAgo, 3, "Publish this week's blog ({period})", KAVYA,
				TaskPriority.MEDIUM, List.of("Final proofread", "Add the target keyword to the title and meta", "Publish and share"));
		save("Monthly backlink verification", Frequency.MONTHLY, threeMonthsAgo, 24, "Verify {month} backlinks", ARUN,
				TaskPriority.MEDIUM, List.of("Check every submitted link", "Mark live and lost links"));
		save("Quarterly SEO audit", Frequency.QUARTERLY, threeMonthsAgo, 20, "{quarter} {year} SEO audit", ARUN,
				TaskPriority.HIGH, List.of("Crawl the site", "Review Core Web Vitals", "Write up the findings"));
		save("Monthly competitor analysis", Frequency.MONTHLY, threeMonthsAgo, 14, null, PRIYA, TaskPriority.MEDIUM, List.of());

		// Last month's ranking update is done; completing it creates this month's occurrence and task.
		User arun = userRepository.findByEmailIgnoreCase(ARUN).orElse(null);
		ActivityOccurrence lastMonth = engine
			.ensure(rankings, Recurrence.containing(Frequency.MONTHLY, today.minusMonths(1)))
			.occurrence();
		lastMonth.getTask().setStatus(TaskStatus.COMPLETED);
		lastMonth.getTask().setCompletedAt(calendar.now());
		engine.close(lastMonth, OccurrenceStatus.COMPLETED, arun == null ? null : arun.getId());

		int created = 0;
		for (MarketingActivity activity : activityRepository.findAll()) {
			engine.ensureCurrent(activity, today);
			created++;
		}
		return created;
	}

	private MarketingActivity save(String name, Frequency frequency, LocalDate start, int offset, String template,
			String assigneeEmail, TaskPriority priority, List<String> checklist) {
		MarketingActivity activity = new MarketingActivity();
		activity.setName(name);
		activity.setDepartment(departmentRepository.findByCode("DM").orElseThrow());
		activity.setOwner(userRepository.findByEmailIgnoreCase(PRIYA).orElse(null));
		activity.setDefaultAssignee(userRepository.findByEmailIgnoreCase(assigneeEmail).orElse(null));
		activity.setFrequency(frequency);
		activity.setStartDate(start);
		activity.setDueOffsetDays(offset);
		activity.setTaskTitleTemplate(template);
		activity.setTaskPriority(priority);
		int position = 0;
		for (String content : checklist) {
			ActivityChecklistItem item = new ActivityChecklistItem();
			item.setActivity(activity);
			item.setContent(content);
			item.setPosition(position++);
			activity.getChecklist().add(item);
		}
		return activityRepository.save(activity);
	}

}
