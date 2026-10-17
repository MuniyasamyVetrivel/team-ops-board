package com.teamops.finance.service;

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

import com.teamops.common.config.BusinessCalendar;
import com.teamops.finance.FinanceModule;
import com.teamops.finance.entity.FinanceLicence;
import com.teamops.finance.entity.LicenceStatus;
import com.teamops.finance.repository.FinanceLicenceRepository;
import com.teamops.module.service.ModuleAccessService;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Licence renewal reminders (brief: 30 and 7 days before), in-app, to the users who may open the Finance Tracker
 * ({@link ModuleAccessService#recipients}: FINANCE_VIEW and the module switched on for them; Super Admin only by
 * default). Active and trial licences only; a trial's renewal date is the day it ends. One renewing in 8 to 30 days
 * gets the 30-day reminder, one renewing in 0 to 7 days the 7-day one, so a missed day still reminds. Each reminder
 * has a dedup key of licence, renewal date, lead time and recipient: re-running never sends one twice, and a new
 * renewal date reminds again.
 */
@Service
@RequiredArgsConstructor
public class FinanceReminderService {

	private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

	private static final int KEY_BATCH = 500;

	private static final Set<LicenceStatus> REMINDED = Set.of(LicenceStatus.ACTIVE, LicenceStatus.TRIAL);

	private final FinanceLicenceRepository licences;

	private final ModuleAccessService moduleAccess;

	private final NotificationRepository notificationRepository;

	private final NotificationService notificationService;

	private final BusinessCalendar calendar;

	/** Sends today's reminders and returns how many notifications were created. */
	@Transactional
	public int sendReminders() {
		List<User> recipients = moduleAccess.recipients(FinanceModule.KEY);
		if (recipients.isEmpty()) {
			return 0;
		}
		LocalDate today = calendar.today();
		Map<String, Delivery> deliveries = new LinkedHashMap<>();
		for (FinanceLicence licence : licences.findRenewalsBetween(today, today.plusDays(LicenceRules.LEAD_DAYS.getLast()),
				REMINDED)) {
			long daysLeft = ChronoUnit.DAYS.between(today, licence.getRenewalDate());
			int lead = LicenceRules.leadFor(daysLeft);
			String title = title(licence, daysLeft);
			String body = body(licence);
			for (User recipient : recipients) {
				deliveries.put(dedupKey(licence, lead, recipient.getId()),
						new Delivery(licence.getId(), recipient.getId(), title, body));
			}
		}
		Set<String> existing = existingKeys(new ArrayList<>(deliveries.keySet()));
		int sent = 0;
		for (Map.Entry<String, Delivery> entry : deliveries.entrySet()) {
			if (!existing.contains(entry.getKey())) {
				Delivery d = entry.getValue();
				notificationService.notify(d.userId(), NotificationType.LICENCE_RENEWAL, d.title(), d.body(),
						NotificationService.ENTITY_FINANCE_LICENCE, d.licenceId(), entry.getKey());
				sent++;
			}
		}
		return sent;
	}

	/** E.g. "Renewal in 7 days: Jira Cloud (Atlassian)", "Trial ends tomorrow: Figma". */
	static String title(FinanceLicence licence, long daysLeft) {
		boolean trial = licence.getStatus() == LicenceStatus.TRIAL;
		String when;
		if (daysLeft == 0) {
			when = trial ? "Trial ends today" : "Renews today";
		}
		else if (daysLeft == 1) {
			when = trial ? "Trial ends tomorrow" : "Renews tomorrow";
		}
		else {
			when = (trial ? "Trial ends in " : "Renewal in ") + daysLeft + " days";
		}
		String vendor = licence.getVendor() == null ? "" : " (" + licence.getVendor().getName() + ")";
		return when + ": " + licence.getName() + vendor;
	}

	/** The date, the seats in use and (finance recipients only) the cost per cycle. */
	static String body(FinanceLicence licence) {
		boolean trial = licence.getStatus() == LicenceStatus.TRIAL;
		StringBuilder body = new StringBuilder(licence.getName()).append(trial ? "'s trial ends on " : " renews on ")
			.append(FULL_DATE.format(licence.getRenewalDate()))
			.append('.');
		int used = licence.getAssignments().size();
		if (licence.getSeatsPaid() != null) {
			body.append(' ').append(used).append(" of ").append(licence.getSeatsPaid()).append(" seats are in use.");
		}
		if (licence.getCost() != null) {
			body.append(" Cost: ").append(licence.getCurrency()).append(' ').append(licence.getCost().toPlainString())
				.append(switch (licence.getBillingCycle()) {
					case MONTHLY -> " a month.";
					case YEARLY -> " a year.";
					case ONE_OFF -> " (one-off).";
				});
		}
		return body.toString();
	}

	static String dedupKey(FinanceLicence licence, int lead, Long userId) {
		return "LICENCE_RENEWAL:" + licence.getId() + ":" + licence.getRenewalDate() + ":" + lead + ":" + userId;
	}

	private Set<String> existingKeys(List<String> keys) {
		Set<String> existing = new HashSet<>();
		for (int start = 0; start < keys.size(); start += KEY_BATCH) {
			existing.addAll(notificationRepository.findExistingDedupKeys(keys.subList(start,
					Math.min(start + KEY_BATCH, keys.size()))));
		}
		return existing;
	}

	private record Delivery(Long licenceId, Long userId, String title, String body) {
	}

}
