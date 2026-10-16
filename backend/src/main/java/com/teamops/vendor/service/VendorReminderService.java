package com.teamops.vendor.service;

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
import com.teamops.module.service.ModuleAccessService;
import com.teamops.notification.entity.NotificationType;
import com.teamops.notification.repository.NotificationRepository;
import com.teamops.notification.service.NotificationService;
import com.teamops.user.entity.User;
import com.teamops.vendor.VendorModule;
import com.teamops.vendor.entity.VendorContract;
import com.teamops.vendor.repository.VendorContractRepository;

import lombok.RequiredArgsConstructor;

/**
 * Contract renewal reminders (brief: 30 and 7 days before), in-app, to the users who may open Vendors
 * ({@link ModuleAccessService#recipients}: VENDOR_VIEW and the module switched on for them; Super Admin only by
 * default). A contract comes up for renewal on its renewal date, or else its end date ({@link ContractRules}). One
 * renewing in 8 to 30 days gets the 30-day reminder, one renewing in 0 to 7 days the 7-day one, so a missed day or a
 * contract added late still gets its reminder. Each reminder has a dedup key of contract, renewal date, lead time and
 * recipient: re-running never sends one twice, and a new renewal date reminds again. Expired contracts are skipped.
 */
@Service
@RequiredArgsConstructor
public class VendorReminderService {

	private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

	private static final int KEY_BATCH = 500;

	private final VendorContractRepository contracts;

	private final ModuleAccessService moduleAccess;

	private final NotificationRepository notificationRepository;

	private final NotificationService notificationService;

	private final BusinessCalendar calendar;

	/** Sends today's reminders and returns how many notifications were created. */
	@Transactional
	public int sendReminders() {
		List<User> recipients = moduleAccess.recipients(VendorModule.KEY);
		if (recipients.isEmpty()) {
			return 0;
		}
		LocalDate today = calendar.today();
		Map<String, Delivery> deliveries = new LinkedHashMap<>();
		for (VendorContract contract : contracts.findRenewalsBetween(today,
				today.plusDays(ContractRules.LEAD_DAYS.getLast()))) {
			LocalDate renewsOn = ContractRules.renewsOn(contract);
			long daysLeft = ChronoUnit.DAYS.between(today, renewsOn);
			int lead = ContractRules.leadFor(daysLeft);
			String title = title(contract, daysLeft);
			String body = body(contract, renewsOn);
			for (User recipient : recipients) {
				deliveries.put(dedupKey(contract, renewsOn, lead, recipient.getId()),
						new Delivery(contract.getId(), recipient.getId(), title, body));
			}
		}
		Set<String> existing = existingKeys(new ArrayList<>(deliveries.keySet()));
		int sent = 0;
		for (Map.Entry<String, Delivery> entry : deliveries.entrySet()) {
			if (!existing.contains(entry.getKey())) {
				Delivery d = entry.getValue();
				notificationService.notify(d.userId(), NotificationType.CONTRACT_RENEWAL, d.title(), d.body(),
						NotificationService.ENTITY_VENDOR_CONTRACT, d.contractId(), entry.getKey());
				sent++;
			}
		}
		return sent;
	}

	/** E.g. "Renewal in 7 days: Atlassian – Jira Cloud", "Renews today: …", "Ends tomorrow: …". */
	static String title(VendorContract contract, long daysLeft) {
		boolean ends = contract.getRenewalDate() == null && !contract.isAutoRenew();
		String when;
		if (daysLeft == 0) {
			when = ends ? "Ends today" : "Renews today";
		}
		else if (daysLeft == 1) {
			when = ends ? "Ends tomorrow" : "Renews tomorrow";
		}
		else {
			when = (ends ? "Ends in " : "Renewal in ") + daysLeft + " days";
		}
		return when + ": " + contract.getVendor().getName() + " – " + contract.getTitle();
	}

	/** What happens on the date, and the last day to give notice when the contract names a notice period. */
	static String body(VendorContract contract, LocalDate renewsOn) {
		StringBuilder body = new StringBuilder(contract.getVendor().getName()).append("'s contract \"")
			.append(contract.getTitle())
			.append('"');
		if (contract.isAutoRenew()) {
			body.append(" renews automatically on ");
		}
		else if (contract.getRenewalDate() == null) {
			body.append(" ends on ");
		}
		else {
			body.append(" is due for renewal on ");
		}
		body.append(FULL_DATE.format(renewsOn)).append('.');
		LocalDate noticeBy = ContractRules.noticeBy(renewsOn, contract.getNoticePeriodDays());
		if (noticeBy != null) {
			body.append(" To cancel or change it, give notice by ").append(FULL_DATE.format(noticeBy)).append('.');
		}
		return body.toString();
	}

	static String dedupKey(VendorContract contract, LocalDate renewsOn, int lead, Long userId) {
		return "CONTRACT_RENEWAL:" + contract.getId() + ":" + renewsOn + ":" + lead + ":" + userId;
	}

	private Set<String> existingKeys(List<String> keys) {
		Set<String> existing = new HashSet<>();
		for (int start = 0; start < keys.size(); start += KEY_BATCH) {
			existing.addAll(notificationRepository.findExistingDedupKeys(keys.subList(start,
					Math.min(start + KEY_BATCH, keys.size()))));
		}
		return existing;
	}

	private record Delivery(Long contractId, Long userId, String title, String body) {
	}

}
