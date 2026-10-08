package com.teamops.marketing.seo.service;

import java.util.Comparator;
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
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.entity.RankingSource;
import com.teamops.marketing.seo.entity.SearchEngine;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.repository.KeywordRankingRepository;
import com.teamops.marketing.seo.repository.SeoKeywordRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Imports monthly positions for existing keywords. Insert-only: a month that already has a ranking is an invalid row
 * (corrections are made on the keyword), and so is a future month or an unknown keyword. Position is required, with
 * "NR" for Not Ranked, so a blank cell is never stored as Not Ranked.
 */
@Component
@RequiredArgsConstructor
public class RankingCsvImporter implements CsvImporter<RankingCsvImporter.Ranking> {

	public static final String TYPE = "seo-rankings";

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("page_url", "The page's URL exactly as on the SEO page", "/services/sap-testing"),
			CsvColumn.required("keyword", "An existing keyword on that page", "SAP Testing Services"),
			CsvColumn.required("month", "1–12", "10"),
			CsvColumn.required("year", "e.g. 2026", "2026"),
			CsvColumn.required("position", "1–100, or NR when not ranked", "7"),
			CsvColumn.optional("search_volume", "Monthly searches", "1900"),
			CsvColumn.optional("notes", "Up to 1000 characters", "Moved after the content refresh"),
			CsvColumn.optional("search_engine", "GOOGLE (default) or BING", "GOOGLE"),
			CsvColumn.optional("location", "Default India", "India"),
			CsvColumn.optional("device", "DESKTOP (default) or MOBILE", "DESKTOP"));

	private final SeoKeywordRepository keywordRepository;

	private final KeywordRankingRepository rankingRepository;

	private final KeywordRankingService rankingService;

	private final UserRepository userRepository;

	private final BusinessCalendar calendar;

	/** A validated row. {@code position} is {@code null} for Not Ranked. */
	public record Ranking(Long keywordId, MarketingPeriod period, Integer position, Integer searchVolume,
			String notes) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public String label() {
		return "Monthly keyword rankings";
	}

	@Override
	public String description() {
		return "One row per keyword and month. Adds months that have no ranking yet; earlier months are never overwritten.";
	}

	@Override
	public String permission() {
		return "SEO_EDIT";
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Ranking> open(AuthenticatedUser actor) {
		// One query for every keyword; matching ignores case, like the database's unique key.
		Map<String, SeoKeyword> keywords = keywordRepository.findAllWithPage()
			.stream()
			.collect(Collectors.toMap(RankingCsvImporter::identity, Function.identity(), (a, b) -> a));
		return new ImportSession<>() {

			@Override
			public Ranking parse(RowReader row) {
				String url = row.url("page_url", 500, true);
				String text = SeoService.normalize(row.text("keyword", 200));
				Integer month = row.integer("month", 1, 12);
				Integer year = row.integer("year", MarketingPeriod.MIN_YEAR, MarketingPeriod.MAX_YEAR);
				String positionText = row.text("position", 20);
				RankingRules.ParsedPosition position = RankingRules.parsePosition(positionText);
				if (positionText != null && position == null) {
					row.error("position", "Use a whole number from 1 to 100, or NR when not ranked");
				}
				Integer volume = row.integer("search_volume", 0, 1_000_000_000);
				String notes = row.text("notes", 1000);
				SearchEngine engine = row.choice("search_engine", SearchEngine.class);
				String location = SeoService.normalize(row.text("location", 100));
				Device device = row.choice("device", Device.class);
				if (!row.valid()) {
					return null;
				}
				engine = engine == null ? SearchEngine.GOOGLE : engine;
				location = location == null ? SeoService.DEFAULT_LOCATION : location;
				device = device == null ? Device.DESKTOP : device;
				SeoKeyword keyword = keywords.get(identity(url, text, engine, location, device));
				if (keyword == null) {
					row.error("keyword", "No keyword \"" + text + "\" (" + engine + ", " + location + ", " + device
							+ ") on page " + url + ". Add it on the SEO page first.");
					return null;
				}
				if (keyword.getStatus() == KeywordStatus.ARCHIVED) {
					row.error("keyword", "This keyword is archived");
				}
				MarketingPeriod period = new MarketingPeriod(month, year);
				if (!RankingRules.isRecordable(period, calendar.today())) {
					row.error("month", "Future months cannot be recorded");
				}
				else if (rankingRepository.findByKeywordIdAndMonthAndYear(keyword.getId(), month, year).isPresent()) {
					row.error(null, period.label() + " is already recorded for this keyword. Correct it on the keyword instead.");
				}
				return row.valid() ? new Ranking(keyword.getId(), period, position.position(), volume, notes) : null;
			}

			@Override
			public String key(Ranking record) {
				return record.keywordId() + ":" + record.period().year() + ":" + record.period().month();
			}

			@Override
			public int commit(List<Ranking> records) {
				User recordedBy = userRepository.getReferenceById(actor.id());
				// Oldest first, so each month's "previous position" snapshot sees the months imported before it.
				List<Ranking> ordered = records.stream()
					.sorted(Comparator.comparing((Ranking r) -> r.period().firstDay()))
					.toList();
				for (Ranking record : ordered) {
					if (rankingRepository
						.findByKeywordIdAndMonthAndYear(record.keywordId(), record.period().month(), record.period().year())
						.isPresent()) {
						throw KeywordRankingService.alreadyRecorded(record.period());
					}
					rankingService.insert(keywordRepository.findById(record.keywordId()).orElseThrow(), record.period(),
							record.position(), record.searchVolume(), record.notes(), RankingSource.CSV, recordedBy);
				}
				rankingService.refreshCaches(ordered.stream().map(Ranking::keywordId).toList());
				return ordered.size();
			}

		};
	}

	private static String identity(SeoKeyword keyword) {
		return identity(keyword.getPage().getUrl(), keyword.getKeyword(), keyword.getSearchEngine(),
				keyword.getLocation(), keyword.getDevice());
	}

	private static String identity(String url, String keyword, SearchEngine engine, String location, Device device) {
		return String.join("\u0000", url.toLowerCase(Locale.ROOT), keyword.toLowerCase(Locale.ROOT), engine.name(),
				location.toLowerCase(Locale.ROOT), device.name());
	}

}
