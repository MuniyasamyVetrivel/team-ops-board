package com.teamops.marketing.paid.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.marketing.common.MarketingMonths;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.paid.entity.AdDataSource;
import com.teamops.marketing.paid.entity.PaidCampaign;
import com.teamops.marketing.paid.entity.PaidCampaignStatus;
import com.teamops.marketing.paid.repository.PaidCampaignMonthRepository;
import com.teamops.marketing.paid.repository.PaidCampaignRepository;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Imports monthly paid campaign results, e.g. a LinkedIn Campaign Manager export summed per month. Rows only add
 * months: a month already recorded for the campaign is rejected (correct it on the campaign instead). The campaign is
 * matched by its external id, or else by its name, which must then be unique.
 */
@Component
@RequiredArgsConstructor
public class PaidResultsCsvImporter implements CsvImporter<PaidResultsCsvImporter.Row> {

	public static final String TYPE = "paid-campaign-results";

	private static final int MAX_COUNT = 1_000_000_000;

	private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("campaign", "Campaign name (or its external id)", "SAP S/4HANA Testing Campaign"),
			CsvColumn.required("month", "1–12", "10"),
			CsvColumn.required("year", "e.g. 2026", "2026"),
			CsvColumn.required("amount_spent", "Spend in the campaign's currency", "42000"),
			CsvColumn.required("impressions", "Impressions", "150000"),
			CsvColumn.required("clicks", "Clicks (at most the impressions)", "2800"),
			CsvColumn.required("leads", "Leads", "84"),
			CsvColumn.optional("conversions", "Conversions (default 0, at most the leads)", "12"),
			CsvColumn.optional("notes", "Up to 1000 characters", ""));

	private final PaidCampaignRepository campaignRepository;

	private final PaidCampaignMonthRepository monthRepository;

	private final PaidCampaignService campaignService;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	public record Row(Long campaignId, MarketingPeriod period, PaidResults results, String notes) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public String label() {
		return "Paid campaign results";
	}

	@Override
	public String description() {
		return "One row per campaign and month (spend, impressions, clicks, leads, conversions). Months already recorded are rejected.";
	}

	@Override
	public String permission() {
		return PaidCampaignService.CAMPAIGN_EDIT;
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Row> open(AuthenticatedUser actor) {
		Map<String, PaidCampaign> byExternalId = new HashMap<>();
		Map<String, PaidCampaign> byName = new HashMap<>();
		Map<String, Integer> nameCounts = new HashMap<>();
		for (PaidCampaign campaign : campaignRepository.findAllByOrderByIdAsc()) {
			if (campaign.getExternalId() != null) {
				byExternalId.putIfAbsent(campaign.getExternalId().toLowerCase(Locale.ROOT), campaign);
			}
			String name = campaign.getName().toLowerCase(Locale.ROOT);
			byName.putIfAbsent(name, campaign);
			nameCounts.merge(name, 1, Integer::sum);
		}
		LocalDate today = calendar.today();
		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String reference = row.text("campaign", 200);
				Integer month = row.integer("month", 1, 12);
				Integer year = row.integer("year", MarketingPeriod.MIN_YEAR, MarketingPeriod.MAX_YEAR);
				BigDecimal spent = row.amount("amount_spent", 2);
				Integer impressions = row.integer("impressions", 0, MAX_COUNT);
				Integer clicks = row.integer("clicks", 0, MAX_COUNT);
				Integer leads = row.integer("leads", 0, MAX_COUNT);
				Integer conversions = row.integer("conversions", 0, MAX_COUNT);
				String notes = row.text("notes", 1000);
				if (!row.valid()) {
					return null;
				}
				if (spent.compareTo(MAX_MONEY) > 0) {
					row.error("amount_spent", "Too large");
				}
				String key = reference.toLowerCase(Locale.ROOT);
				PaidCampaign campaign = byExternalId.get(key);
				if (campaign == null && nameCounts.getOrDefault(key, 0) > 1) {
					row.error("campaign", "More than one campaign has this name; use its external id");
					return null;
				}
				if (campaign == null) {
					campaign = byName.get(key);
				}
				if (campaign == null) {
					row.error("campaign", "No campaign with this name or external id");
					return null;
				}
				MarketingPeriod period = new MarketingPeriod(month, year);
				PaidResults results = new PaidResults(spent, impressions, clicks, leads,
						conversions == null ? 0 : conversions);
				if (campaign.getStatus() == PaidCampaignStatus.DRAFT) {
					row.error("campaign", "This campaign is a draft; set it to active first");
				}
				if (MarketingMonths.isFuture(period, today)) {
					row.error("month", "Results cannot be recorded for a future month");
				}
				else if (!campaign.runsIn(period)) {
					row.error("month", period.label() + " is outside the campaign's dates");
				}
				for (PaidResults.Problem problem : results.problems()) {
					row.error(problem.field(), problem.message());
				}
				if (monthRepository.findByCampaignIdAndMonthAndYear(campaign.getId(), month, year).isPresent()) {
					row.error("month", period.label() + " is already recorded for this campaign; correct it on the campaign");
				}
				return row.valid() ? new Row(campaign.getId(), period, results, notes) : null;
			}

			@Override
			public String key(Row record) {
				return record.campaignId() + "|" + record.period().year() + "-" + record.period().month();
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					PaidCampaign campaign = campaignRepository.getReferenceById(record.campaignId());
					campaignService.insertMonth(campaign, record.period(), record.results(), record.notes(),
							AdDataSource.CSV, userRepository.getReferenceById(actor.id()));
					Map<String, Object> details = new HashMap<>();
					details.put("month", record.period().month());
					details.put("year", record.period().year());
					details.put("results", record.results());
					details.put("source", AdDataSource.CSV);
					auditService.record(AuditAction.PAID_RESULTS_RECORDED, actor.id(), "PAID_CAMPAIGN", record.campaignId(),
							details, null);
				}
				return records.size();
			}

		};
	}

}
