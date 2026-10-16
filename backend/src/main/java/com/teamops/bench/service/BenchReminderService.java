package com.teamops.bench.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.bench.BenchModule;
import com.teamops.bench.dto.BenchDtos.ConsultantRef;
import com.teamops.bench.entity.Allocation;
import com.teamops.bench.entity.EmploymentStatus;
import com.teamops.bench.repository.AllocationRepository;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Roll-off reminders (brief: 30 and 14 days before an allocation ends), in-app, to the users who may open Bench
 * Management ({@link ModuleAccessService#recipients}: BENCH_VIEW and the module switched on for them; Super Admin only
 * by default). An allocation ending in 15 to 30 days gets the 30-day reminder, one ending in 0 to 14 days the 14-day
 * one, so a missed day or an allocation added late still gets its reminder. Each reminder has a dedup key of
 * allocation, end date, lead time and recipient: re-running never sends one twice, and a new end date reminds again.
 * Consultants who have left are skipped.
 */
@Service
@RequiredArgsConstructor
public class BenchReminderService {

	public static final List<Integer> LEAD_DAYS = List.of(14, 30);

	private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

	private static final int KEY_BATCH = 500;

	private final AllocationRepository allocations;

	private final ModuleAccessService moduleAccess;

	private final NotificationRepository notificationRepository;

	private final NotificationService notificationService;

	private final BusinessCalendar calendar;

	/** Sends today's reminders and returns how many notifications were created. */
	@Transactional
	public int sendReminders() {
		List<User> recipients = moduleAccess.recipients(BenchModule.KEY);
		if (recipients.isEmpty()) {
			return 0;
		}
		LocalDate today = calendar.today();
		int longest = LEAD_DAYS.getLast();
		Map<String, Delivery> deliveries = new LinkedHashMap<>();
		for (Allocation allocation : allocations.findByEndDateBetweenOrderByEndDateAscIdAsc(today,
				today.plusDays(longest))) {
			if (allocation.getConsultant().getEmploymentStatus() == EmploymentStatus.LEFT) {
				continue;
			}
			long daysLeft = ChronoUnit.DAYS.between(today, allocation.getEndDate());
			int lead = leadFor(daysLeft);
			String title = title(allocation, daysLeft);
			String body = body(allocation);
			for (User recipient : recipients) {
				deliveries.put(dedupKey(allocation, lead, recipient.getId()),
						new Delivery(allocation.getId(), recipient.getId(), title, body));
			}
		}
		Set<String> existing = existingKeys(new ArrayList<>(deliveries.keySet()));
		int sent = 0;
		for (Map.Entry<String, Delivery> entry : deliveries.entrySet()) {
			if (!existing.contains(entry.getKey())) {
				Delivery d = entry.getValue();
				notificationService.notify(d.userId(), NotificationType.BENCH_ROLL_OFF, d.title(), d.body(),
						NotificationService.ENTITY_ALLOCATION, d.allocationId(), entry.getKey());
				sent++;
			}
		}
		return sent;
	}

	/** The reminder an allocation ending in {@code daysLeft} days is due: the smallest lead time it is within. */
	static int leadFor(long daysLeft) {
		return LEAD_DAYS.stream().filter(lead -> daysLeft <= lead).findFirst().orElseThrow();
	}

	/** E.g. "Roll-off in 14 days: Priya Menon (Acme – S/4 rollout)", "Rolls off today: …". */
	static String title(Allocation allocation, long daysLeft) {
		String when = daysLeft == 0 ? "Rolls off today" : daysLeft == 1 ? "Rolls off tomorrow"
				: "Roll-off in " + daysLeft + " days";
		return when + ": " + name(allocation) + " (" + allocation.getEngagement().getClient().getName() + " – "
				+ allocation.getEngagement().getName() + ")";
	}

	static String body(Allocation allocation) {
		return name(allocation) + "'s allocation as " + allocation.getRole() + " (" + allocation.getAllocationPercent()
				+ "%, " + (allocation.isBillable() ? "billable" : "non-billable") + ") ends on "
				+ FULL_DATE.format(allocation.getEndDate()) + ".";
	}

	static String dedupKey(Allocation allocation, int lead, Long userId) {
		return "BENCH_ROLL_OFF:" + allocation.getId() + ":" + allocation.getEndDate() + ":" + lead + ":" + userId;
	}

	private static String name(Allocation allocation) {
		ConsultantRef consultant = ConsultantRef.of(allocation.getConsultant());
		return consultant.fullName() == null ? "Consultant #" + consultant.id() : consultant.fullName();
	}

	private Set<String> existingKeys(List<String> keys) {
		Set<String> existing = new HashSet<>();
		for (int start = 0; start < keys.size(); start += KEY_BATCH) {
			existing.addAll(notificationRepository.findExistingDedupKeys(keys.subList(start,
					Math.min(start + KEY_BATCH, keys.size()))));
		}
		return existing;
	}

	private record Delivery(Long allocationId, Long userId, String title, String body) {
	}

}
