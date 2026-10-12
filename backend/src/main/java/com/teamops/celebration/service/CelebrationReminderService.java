package com.teamops.celebration.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.celebration.dto.CelebrationDtos.LinkedInPostDto;
import com.teamops.celebration.entity.LinkedInPost;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.user.entity.RoleCodes;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily reminders for birthdays, work anniversaries, important days and events: the "Reminder days before" setting
 * (default 2) ahead, and again on the day, to every active Super Admin and CELEBRATIONS_EDIT holder. "Today" comes
 * from {@link BusinessCalendar} (APP_TIME_ZONE), like everywhere else. Each reminder has a dedup key of occurrence, lead
 * time and recipient, so re-running the job never sends one twice. In-app only: no email sender is configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CelebrationReminderService {

	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

	private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

	private static final int KEY_BATCH = 500;

	private final OccasionFinder finder;

	private final LinkedInPostService linkedInPosts;

	private final UserRepository userRepository;

	private final NotificationRepository notificationRepository;

	private final NotificationService notificationService;

	private final AppSettingsService settings;

	private final BusinessCalendar calendar;

	/** Sends today's reminders and returns how many notifications were created. */
	@Transactional
	public int sendReminders() {
		LocalDate today = calendar.today();
		List<User> recipients = recipients();
		if (recipients.isEmpty()) {
			return 0;
		}
		Set<Integer> leads = new TreeSet<>(List.of(settings.celebrationReminderDaysBefore(), 0));
		List<Reminder> reminders = new ArrayList<>();
		for (int daysAhead : leads) {
			LocalDate day = today.plusDays(daysAhead);
			List<Occasion> occasions = finder.between(day, day, false)
				.stream()
				.filter(o -> o.date().equals(day))
				.toList();
			Map<String, LinkedInPost> posts = linkedInPosts.forOccasions(occasions);
			for (Occasion occasion : occasions) {
				LinkedInPostDto linkedIn = LinkedInPostService.dto(occasion.kind(), posts.get(occasion.key()));
				reminders.add(new Reminder(occasion, daysAhead, title(occasion, daysAhead, linkedIn), body(occasion)));
			}
		}
		Map<String, Delivery> deliveries = new LinkedHashMap<>();
		for (Reminder reminder : reminders) {
			for (User recipient : recipients) {
				deliveries.put(dedupKey(reminder.occasion(), reminder.daysAhead(), recipient.getId()),
						new Delivery(reminder, recipient.getId()));
			}
		}
		Set<String> existing = existingKeys(new ArrayList<>(deliveries.keySet()));
		int sent = 0;
		for (Map.Entry<String, Delivery> entry : deliveries.entrySet()) {
			if (existing.contains(entry.getKey())) {
				continue;
			}
			Reminder reminder = entry.getValue().reminder();
			Occasion occasion = reminder.occasion();
			notificationService.notify(entry.getValue().userId(), NotificationType.OCCASION_REMINDER, reminder.title(),
					reminder.body(), entityType(occasion), occasion.subjectId(), entry.getKey());
			sent++;
		}
		return sent;
	}

	/** Active Super Admins and CELEBRATIONS_EDIT holders, once each. */
	List<User> recipients() {
		Map<Long, User> users = new LinkedHashMap<>();
		userRepository.findByRoleAndStatus(RoleCodes.SUPER_ADMIN, UserStatus.ACTIVE).forEach(u -> users.put(u.getId(), u));
		userRepository.findActiveWithPermission(CelebrationAccess.EDIT_PERMISSION, UserStatus.ACTIVE)
			.forEach(u -> users.putIfAbsent(u.getId(), u));
		return new ArrayList<>(users.values());
	}

	/**
	 * E.g. "Reminder: Priya Menon's birthday is in 2 days (14 October)", "Reminder: Diwali (India) is in 2 days.
	 * LinkedIn post: Not started", "Today: Arun Kumar's work anniversary (3 November): 5 years at Tritusa". The post
	 * status is added when the occurrence needs a LinkedIn post.
	 */
	static String title(Occasion o, int daysAhead, LinkedInPostDto linkedIn) {
		String when = daysAhead == 0 ? "" : daysAhead == 1 ? " is tomorrow" : " is in " + daysAhead + " days";
		String date = " (" + DAY_MONTH.format(o.date()) + ")";
		String text = switch (o.kind()) {
			case BIRTHDAY -> subject(o) + when + date;
			case WORK_ANNIVERSARY -> subject(o) + when + date + ": " + o.detail();
			case IMPORTANT_DAY -> subject(o) + when;
			case EVENT -> subject(o) + when + date;
		};
		if (linkedIn.needed()) {
			text += ". LinkedIn post: " + linkedIn.status().label();
		}
		return (daysAhead == 0 ? "Today: " : "Reminder: ") + text;
	}

	private static String subject(Occasion o) {
		return switch (o.kind()) {
			case BIRTHDAY -> o.name() + "'s birthday";
			case WORK_ANNIVERSARY -> o.name() + "'s work anniversary";
			case IMPORTANT_DAY -> o.name() + " (" + o.region().label() + ")";
			case EVENT -> o.name() + " (" + o.eventType().label() + ")";
		};
	}

	/** The full date, and for events the start time (in the business time zone) and location. */
	private String body(Occasion o) {
		if (o.startAt() == null) {
			return FULL_DATE.format(o.date());
		}
		ZoneId zone = calendar.zone();
		var start = o.startAt().atZone(zone);
		String text = FULL_DATE.format(start) + ", " + TIME.format(start).toLowerCase(Locale.ENGLISH) + " ("
				+ zone.getId() + ")";
		return o.location() == null ? text : text + " · " + o.location();
	}

	static String dedupKey(Occasion o, int daysAhead, Long userId) {
		return "OCCASION:" + o.kind() + ":" + o.subjectId() + ":" + o.date() + ":" + daysAhead + ":" + userId;
	}

	private static String entityType(Occasion o) {
		return switch (o.kind()) {
			case BIRTHDAY, WORK_ANNIVERSARY -> NotificationService.ENTITY_EMPLOYEE;
			case IMPORTANT_DAY -> NotificationService.ENTITY_IMPORTANT_DAY;
			case EVENT -> NotificationService.ENTITY_MARKETING_EVENT;
		};
	}

	private Set<String> existingKeys(List<String> keys) {
		Set<String> existing = new HashSet<>();
		for (int start = 0; start < keys.size(); start += KEY_BATCH) {
			existing.addAll(notificationRepository.findExistingDedupKeys(keys.subList(start,
					Math.min(start + KEY_BATCH, keys.size()))));
		}
		return existing;
	}

	private record Reminder(Occasion occasion, int daysAhead, String title, String body) {
	}

	private record Delivery(Reminder reminder, Long userId) {
	}

}
