package com.teamops.marketing.email.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.marketing.email.entity.CampaignProvider;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.entity.EmailCampaignStatus;
import com.teamops.marketing.email.entity.EmailCampaignType;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Imports sent email campaigns, e.g. a Zoho Campaigns export. Every row is a SENT campaign. {@code external_id} (the
 * Zoho campaign id) stops the same campaign being imported twice. Blank total opens/clicks default to the unique
 * counts, and blank bounces to sent − delivered.
 */
@Component
@RequiredArgsConstructor
public class EmailCampaignCsvImporter implements CsvImporter<EmailCampaignCsvImporter.Row> {

	public static final String TYPE = "email-campaigns";

	private static final int MAX_COUNT = 100_000_000;

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("name", "Campaign name", "SAP Testing Services Outreach"),
			CsvColumn.required("campaign_type", "NEWSLETTER, LEAD_GENERATION, PRODUCT_PROMOTION, EVENT, RECRUITMENT or OTHER", "LEAD_GENERATION"),
			CsvColumn.required("campaign_date", "Send date, YYYY-MM-DD", "2026-10-06"),
			CsvColumn.required("emails_sent", "Emails sent", "25000"),
			CsvColumn.required("delivered", "Emails delivered", "24000"),
			CsvColumn.required("unique_opens", "Unique opens", "8500"),
			CsvColumn.required("unique_clicks", "Unique clicks", "1250"),
			CsvColumn.optional("leads", "Leads generated (default 0)", "185"),
			CsvColumn.optional("bounced", "Default: sent − delivered", "1000"),
			CsvColumn.optional("opened", "Total opens (default: unique opens)", "9400"),
			CsvColumn.optional("clicked", "Total clicks (default: unique clicks)", "1400"),
			CsvColumn.optional("unsubscribed", "Default 0", "60"),
			CsvColumn.optional("audience", "Who it was sent to", "SAP decision makers"),
			CsvColumn.optional("owner_email", "A Digital Marketing user", "priya.menon@teamops.local"),
			CsvColumn.optional("notes", "Up to 2000 characters", ""),
			CsvColumn.optional("external_id", "The campaign's id in Zoho (prevents duplicates)", "ZC-1042"));

	private final EmailCampaignRepository campaignRepository;

	private final UserRepository userRepository;

	private final BusinessCalendar calendar;

	public record Row(String name, EmailCampaignType type, LocalDate date, EmailCounts counts, String audience,
			Long ownerId, String notes, String externalId) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public String label() {
		return "Email campaigns";
	}

	@Override
	public String description() {
		return "Sent campaigns with their counts, e.g. a Zoho Campaigns export. Rows with an external id already imported are rejected.";
	}

	@Override
	public String permission() {
		return "CAMPAIGN_EDIT";
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Row> open(AuthenticatedUser actor) {
		Map<String, User> owners = userRepository.findActiveWithPermission(MarketingContextService.MARKETING_VIEW, UserStatus.ACTIVE)
			.stream()
			.collect(Collectors.toMap(u -> u.getEmail().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
		LocalDate today = calendar.today();
		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String name = row.text("name", 200);
				EmailCampaignType type = row.choice("campaign_type", EmailCampaignType.class);
				LocalDate date = row.date("campaign_date");
				Integer sent = row.integer("emails_sent", 0, MAX_COUNT);
				Integer delivered = row.integer("delivered", 0, MAX_COUNT);
				Integer uniqueOpens = row.integer("unique_opens", 0, MAX_COUNT);
				Integer uniqueClicks = row.integer("unique_clicks", 0, MAX_COUNT);
				Integer leads = row.integer("leads", 0, MAX_COUNT);
				Integer bounced = row.integer("bounced", 0, MAX_COUNT);
				Integer opened = row.integer("opened", 0, MAX_COUNT);
				Integer clicked = row.integer("clicked", 0, MAX_COUNT);
				Integer unsubscribed = row.integer("unsubscribed", 0, MAX_COUNT);
				String audience = row.text("audience", 200);
				String ownerEmail = row.email("owner_email");
				String notes = row.text("notes", 2000);
				String externalId = row.text("external_id", 100);
				if (!row.valid()) {
					return null;
				}
				if (date.isAfter(today)) {
					row.error("campaign_date", "A sent campaign cannot be dated in the future");
				}
				Long ownerId = null;
				if (ownerEmail != null) {
					User owner = owners.get(ownerEmail);
					if (owner == null) {
						row.error("owner_email", "Not an active Digital Marketing user");
					}
					else {
						ownerId = owner.getId();
					}
				}
				EmailCounts counts = new EmailCounts(sent, delivered, bounced == null ? Math.max(sent - delivered, 0) : bounced,
						opened == null ? uniqueOpens : opened, uniqueOpens, clicked == null ? uniqueClicks : clicked,
						uniqueClicks, unsubscribed == null ? 0 : unsubscribed, leads == null ? 0 : leads);
				for (EmailCounts.Problem problem : counts.problems()) {
					row.error(column(problem.field()), problem.message());
				}
				if (externalId != null && campaignRepository.existsByProviderAndExternalId(CampaignProvider.CSV, externalId)) {
					row.error("external_id", "This campaign was already imported");
				}
				return row.valid() ? new Row(name, type, date, counts, audience, ownerId, notes, externalId) : null;
			}

			@Override
			public String key(Row record) {
				return record.externalId() != null ? "id:" + record.externalId()
						: record.name().toLowerCase(Locale.ROOT) + "|" + record.date();
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					EmailCampaign campaign = new EmailCampaign();
					campaign.setName(record.name());
					campaign.setCampaignType(record.type());
					campaign.setCampaignDate(record.date());
					campaign.setStatus(EmailCampaignStatus.SENT);
					campaign.apply(record.counts());
					campaign.setAudience(record.audience());
					campaign.setOwner(record.ownerId() == null ? null : userRepository.getReferenceById(record.ownerId()));
					campaign.setNotes(record.notes());
					campaign.setProvider(CampaignProvider.CSV);
					campaign.setExternalId(record.externalId());
					campaignRepository.save(campaign);
				}
				return records.size();
			}

		};
	}

	/** "uniqueOpens" → "unique_opens" (the CSV column). */
	private static String column(String field) {
		return field.equals("leads") ? "leads" : field.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
	}

}
