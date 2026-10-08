package com.teamops.marketing.seo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.common.RankingChange;
import com.teamops.marketing.common.RankingStatus;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.seo.dto.RankingDtos.CorrectRanking;
import com.teamops.marketing.seo.dto.RankingDtos.KeywordHistory;
import com.teamops.marketing.seo.dto.RankingDtos.KeywordSeries;
import com.teamops.marketing.seo.dto.RankingDtos.MonthlyEntry;
import com.teamops.marketing.seo.dto.RankingDtos.MonthlyReport;
import com.teamops.marketing.seo.dto.RankingDtos.PageHistory;
import com.teamops.marketing.seo.dto.RankingDtos.Point;
import com.teamops.marketing.seo.dto.RankingDtos.RankingEntry;
import com.teamops.marketing.seo.dto.RankingDtos.RecordMonthly;
import com.teamops.marketing.seo.dto.RankingDtos.RecordRanking;
import com.teamops.marketing.seo.dto.RankingDtos.RecordResult;
import com.teamops.marketing.seo.dto.SeoDtos.KeywordItem;
import com.teamops.marketing.seo.dto.SeoDtos.PageRef;
import com.teamops.marketing.seo.entity.KeywordRanking;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.entity.RankingSource;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.repository.KeywordRankingRepository;
import com.teamops.marketing.seo.repository.SeoKeywordRepository;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.marketing.seo.repository.SeoRankingQuery;
import com.teamops.marketing.seo.repository.SeoRankingTableQuery;
import com.teamops.marketing.seo.repository.SeoRankingTableQuery.Filter;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Monthly keyword rankings (brief sections 25, 27 and 29). History is insert-only per month: recording a month adds a
 * row and never changes another month's position. A month's own row can be corrected (audited) while the month is
 * open, see {@link RankingRules}. After every change the keyword's cached positions are refreshed from the history.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordRankingService {

	public static final int EXPORT_LIMIT = 5_000;

	public static final int DEFAULT_HISTORY_MONTHS = 12;

	public static final int MAX_HISTORY_MONTHS = 24;

	private static final String ENTITY = "SEO_RANKING";

	private final SeoKeywordRepository keywordRepository;

	private final SeoPageRepository pageRepository;

	private final KeywordRankingRepository rankingRepository;

	private final SeoRankingQuery rankingQuery;

	private final SeoRankingTableQuery tableQuery;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	// --- reading ----------------------------------------------------------------------------------------------

	/** The SEO ranking table: keywords with their standing in the month, filtered and sorted on that standing. */
	public PageResponse<KeywordItem> table(Filter filter, MarketingPeriod period, String sort, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = size < 1 ? PageRequests.DEFAULT_SIZE : Math.min(size, PageRequests.MAX_SIZE);
		SeoRankingTableQuery.PageOfIds ids = tableQuery.search(filter, period, sort, safePage, safeSize);
		int totalPages = (int) ((ids.total() + safeSize - 1) / safeSize);
		return new PageResponse<>(items(ids.ids(), period), safePage, safeSize, ids.total(), totalPages);
	}

	/** Bucket counts for the month and the month before (brief section 29), over keywords that are not archived. */
	public MonthlyReport monthlyReport(Long pageId, Long ownerId, MarketingPeriod period) {
		MarketingPeriod previous = period.previous();
		return new MonthlyReport(Period.of(period), stats(pageId, ownerId, period), Period.of(previous),
				stats(pageId, ownerId, previous));
	}

	/** {@code correctable} on each entry is evaluated for {@code viewer} (a Super Admin may correct any past month). */
	public KeywordHistory history(Long keywordId, AuthenticatedUser viewer) {
		SeoKeyword keyword = loadKeyword(keywordId);
		LocalDate today = calendar.today();
		List<KeywordRanking> rows = rankingRepository.findByKeywordIdOrderByYearDescMonthDesc(keywordId);
		Map<MarketingPeriod, KeywordRanking> byPeriod = rows.stream()
			.collect(Collectors.toMap(KeywordRankingService::periodOf, Function.identity()));
		List<RankingEntry> entries = rows.stream().map(row -> {
			MarketingPeriod period = periodOf(row);
			KeywordRanking previous = byPeriod.get(period.previous());
			RankingChange change = RankingChange.of(previous != null,
					previous == null ? null : previous.getPosition(), row.getPosition());
			return new RankingEntry(row.getId(), period.month(), period.year(), period.label(), row.getPosition(),
					RankingStatus.of(row.getPosition()), change, row.getSearchVolume(), row.getNotes(),
					row.getSource(), UserSummary.of(row.getRecordedBy()), row.getCreatedAt(), row.getUpdatedAt(),
					row.getVersion(), RankingRules.canCorrect(period, today, viewer.isSuperAdmin()));
		}).toList();
		return new KeywordHistory(keyword.getId(), keyword.getKeyword(), PageRef.of(keyword.getPage()),
				keyword.getSearchEngine(), keyword.getLocation(), keyword.getDevice(), keyword.getTargetPosition(),
				keyword.getStatus(), entries);
	}

	/** Positions of a page's keywords (not archived) for {@code months} months ending with {@code end}. */
	public PageHistory pageHistory(Long pageId, MarketingPeriod end, Integer months) {
		pageRepository.findById(pageId)
			.orElseThrow(() -> ApiException.notFound("SEO_PAGE_NOT_FOUND", "Page not found"));
		int count = months == null ? DEFAULT_HISTORY_MONTHS : Math.clamp(months, 1, MAX_HISTORY_MONTHS);
		List<MarketingPeriod> periods = new ArrayList<>();
		for (int i = count - 1; i >= 0; i--) {
			periods.add(end.plusMonths(-i));
		}
		Map<Long, Map<MarketingPeriod, Integer>> positions = new HashMap<>();
		Map<Long, Set<MarketingPeriod>> recorded = new HashMap<>();
		for (SeoRankingTableQuery.HistoryPoint point : tableQuery.pageHistory(pageId, periods.getFirst(), end)) {
			recorded.computeIfAbsent(point.keywordId(), id -> new HashSet<>()).add(point.period());
			if (point.position() != null) {
				positions.computeIfAbsent(point.keywordId(), id -> new HashMap<>()).put(point.period(), point.position());
			}
		}
		List<KeywordSeries> series = keywordRepository
			.findByPageIdAndStatusNotOrderByKeywordAscIdAsc(pageId, KeywordStatus.ARCHIVED)
			.stream()
			.map(keyword -> new KeywordSeries(keyword.getId(), keyword.getKeyword(), keyword.getDevice(),
					periods.stream()
						.map(period -> new Point(recorded.getOrDefault(keyword.getId(), Set.of()).contains(period),
								positions.getOrDefault(keyword.getId(), Map.of()).get(period)))
						.toList()))
			.toList();
		List<BigDecimal> averages = new ArrayList<>();
		for (int i = 0; i < periods.size(); i++) {
			long sum = 0;
			int ranked = 0;
			for (KeywordSeries keywordSeries : series) {
				Integer position = keywordSeries.points().get(i).position();
				if (position != null) {
					sum += position;
					ranked++;
				}
			}
			averages.add(MarketingMath.perUnit(sum, ranked));
		}
		return new PageHistory(periods.stream().map(Period::of).toList(), series, averages);
	}

	/** The ranking table as CSV, in table order (at most {@link #EXPORT_LIMIT} rows). */
	public byte[] export(Filter filter, MarketingPeriod period, String sort) {
		List<KeywordItem> items = items(tableQuery.all(filter, period, sort, EXPORT_LIMIT), period);
		ZoneId zone = calendar.zone();
		List<List<String>> rows = items.stream().map(item -> {
			KeywordStanding standing = item.ranking();
			RankingChange change = standing.change();
			List<String> row = new ArrayList<>();
			row.add(item.page().title());
			row.add(item.page().url());
			row.add(item.keyword());
			row.add(item.searchEngine().name());
			row.add(item.location());
			row.add(item.device().name());
			row.add(String.valueOf(period.month()));
			row.add(String.valueOf(period.year()));
			row.add(!standing.recorded() ? "" : positionText(standing.position()));
			row.add(!standing.previousRecorded() ? "" : positionText(standing.previousPosition()));
			row.add(change == null ? "" : change.value() != null ? change.value().toString() : change.movement().name());
			row.add(standing.recorded() ? standing.status().label() : "NO DATA");
			Integer volume = item.entry() != null && item.entry().searchVolume() != null ? item.entry().searchVolume()
					: item.searchVolume();
			row.add(volume == null ? "" : volume.toString());
			row.add(item.owner() == null ? "" : item.owner().fullName());
			row.add(item.entry() == null || item.entry().updatedAt() == null ? ""
					: item.entry().updatedAt().atZone(zone).toLocalDate().toString());
			return row;
		}).toList();
		return CsvWriter.write(List.of("Page", "Page URL", "Keyword", "Search engine", "Location", "Device", "Month",
				"Year", "Position", "Previous position", "Change", "Status", "Search volume", "Owner", "Last updated"),
				rows);
	}

	// --- recording --------------------------------------------------------------------------------------------

	/** Records a month that has no ranking yet. */
	@Transactional
	public KeywordHistory record(Long keywordId, RecordRanking request, AuthenticatedUser actor, ClientInfo client) {
		SeoKeyword keyword = loadKeyword(keywordId);
		requireTracked(keyword);
		MarketingPeriod period = new MarketingPeriod(request.month(), request.year());
		RankingRules.requireRecordable(period, calendar.today());
		Integer position = RankingRules.requireValidPosition(request.position());
		if (rankingRepository.findByKeywordIdAndMonthAndYear(keywordId, period.month(), period.year()).isPresent()) {
			throw alreadyRecorded(period);
		}
		KeywordRanking saved = insert(keyword, period, position, request.searchVolume(), request.notes(),
				RankingSource.MANUAL, userRepository.getReferenceById(actor.id()));
		auditRecorded(saved, keywordId, period, actor, client);
		refreshCaches(List.of(keywordId));
		return history(keywordId, actor);
	}

	/** The monthly update: many keywords for one month, all or nothing. */
	@Transactional
	public RecordResult recordMonthly(RecordMonthly request, AuthenticatedUser actor, ClientInfo client) {
		MarketingPeriod period = new MarketingPeriod(request.month(), request.year());
		RankingRules.requireRecordable(period, calendar.today());
		List<Long> ids = request.entries().stream().map(MonthlyEntry::keywordId).toList();
		if (new LinkedHashSet<>(ids).size() != ids.size()) {
			throw ApiException.badRequest("DUPLICATE_ENTRY", "Each keyword can appear only once");
		}
		Map<Long, SeoKeyword> keywords = keywordRepository.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(SeoKeyword::getId, Function.identity()));
		if (keywords.size() != ids.size()) {
			throw ApiException.badRequest("UNKNOWN_KEYWORD", "One or more keywords no longer exist. Reload and try again.");
		}
		keywords.values().forEach(KeywordRankingService::requireTracked);
		List<Long> already = rankingRepository.findRecordedKeywordIds(ids, period.month(), period.year());
		if (!already.isEmpty()) {
			String names = already.stream().map(id -> keywords.get(id).getKeyword()).sorted().limit(5)
				.collect(Collectors.joining(", "));
			throw ApiException.conflict("RANKING_EXISTS", already.size() + " of these keywords already have a ranking for "
					+ period.label() + " (" + names + "). Correct those on the keyword instead.");
		}
		User recordedBy = userRepository.getReferenceById(actor.id());
		for (MonthlyEntry entry : request.entries()) {
			KeywordRanking saved = insert(keywords.get(entry.keywordId()), period,
					RankingRules.requireValidPosition(entry.position()), entry.searchVolume(), entry.notes(),
					RankingSource.MANUAL, recordedBy);
			auditRecorded(saved, entry.keywordId(), period, actor, client);
		}
		refreshCaches(ids);
		return new RecordResult(Period.of(period), ids.size());
	}

	/**
	 * Corrects a recorded month while it is open (current business month or the one before); a Super Admin may correct
	 * any past month. Always audited; closed-month corrections are flagged with {@code closedMonth}.
	 */
	@Transactional
	public KeywordHistory correct(Long rankingId, CorrectRanking request, AuthenticatedUser actor, ClientInfo client) {
		KeywordRanking row = rankingRepository.findDetailedById(rankingId)
			.orElseThrow(() -> ApiException.notFound("RANKING_NOT_FOUND", "Ranking not found"));
		MarketingPeriod period = periodOf(row);
		LocalDate today = calendar.today();
		if (!RankingRules.canCorrect(period, today, actor.isSuperAdmin())) {
			throw ApiException.conflict("MONTH_LOCKED",
					period.label() + " is closed. Only the current and previous month can be corrected.");
		}
		boolean closedMonth = !RankingRules.isCorrectable(period, today);
		if (!Objects.equals(row.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE",
					"Someone else changed this ranking just now. Reload and try again.");
		}
		Integer position = RankingRules.requireValidPosition(request.position());
		String notes = StringUtils.hasText(request.notes()) ? request.notes().trim() : null;
		AuditChanges changes = new AuditChanges().track("position", row.getPosition(), position)
			.track("searchVolume", row.getSearchVolume(), request.searchVolume())
			.track("notes", row.getNotes(), notes);
		Long keywordId = row.getKeyword().getId();
		if (changes.isEmpty()) {
			return history(keywordId, actor);
		}
		row.setPosition(position);
		row.setSearchVolume(request.searchVolume());
		row.setNotes(notes);
		applySnapshot(row);
		// The next month's snapshot of "previous position" follows the correction; its own position is untouched.
		rankingRepository.findByKeywordIdAndMonthAndYear(keywordId, period.next().month(), period.next().year())
			.ifPresent(this::applySnapshot);
		rankingRepository.flush();
		Map<String, Object> details = new HashMap<>(changes.toDetails());
		details.put("keywordId", keywordId);
		details.put("month", period.month());
		details.put("year", period.year());
		if (closedMonth) {
			// A Super Admin override of a closed month, flagged so it stands out in the audit log.
			details.put("closedMonth", true);
		}
		auditService.record(AuditAction.SEO_RANKING_CORRECTED, actor.id(), ENTITY, rankingId, details, client);
		refreshCaches(List.of(keywordId));
		return history(keywordId, actor);
	}

	/**
	 * Inserts a month that has no row yet (manual entry and CSV import). Snapshots the previous month into the new row
	 * and refreshes the next month's snapshot when it exists (a backfilled month). Call {@link #refreshCaches} after
	 * the last insert.
	 */
	@Transactional
	public KeywordRanking insert(SeoKeyword keyword, MarketingPeriod period, Integer position, Integer searchVolume,
			String notes, RankingSource source, User recordedBy) {
		KeywordRanking row = new KeywordRanking();
		row.setKeyword(keyword);
		row.setPage(keyword.getPage());
		row.setMonth(period.month());
		row.setYear(period.year());
		row.setPosition(position);
		row.setSearchVolume(searchVolume);
		row.setNotes(StringUtils.hasText(notes) ? notes.trim() : null);
		row.setSource(source);
		row.setRecordedBy(recordedBy);
		applySnapshot(row);
		KeywordRanking saved = rankingRepository.save(row);
		rankingRepository
			.findByKeywordIdAndMonthAndYear(keyword.getId(), period.next().month(), period.next().year())
			.ifPresent(this::applySnapshot);
		return saved;
	}

	/** Re-reads each keyword's cached positions from its history. Clears the persistence context. */
	@Transactional
	public void refreshCaches(Collection<Long> keywordIds) {
		new LinkedHashSet<>(keywordIds).forEach(keywordRepository::refreshRankingCache);
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private List<KeywordItem> items(List<Long> ids, MarketingPeriod period) {
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<Long, SeoKeyword> keywords = keywordRepository.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(SeoKeyword::getId, Function.identity()));
		Map<Long, SeoRankingQuery.Row> rows = rankingQuery.forKeywords(ids, period)
			.stream()
			.collect(Collectors.toMap(SeoRankingQuery.Row::keywordId, Function.identity()));
		return ids.stream()
			.filter(keywords::containsKey)
			.map(id -> SeoService.toItem(keywords.get(id), rows.get(id)))
			.toList();
	}

	private SeoStats stats(Long pageId, Long ownerId, MarketingPeriod period) {
		return SeoStats.of(rankingQuery.forReport(pageId, ownerId, period)
			.stream()
			.map(SeoRankingQuery.Row::standing)
			.toList());
	}

	/** Stores the previous month's position and the change at the time of recording (the brief's snapshot). */
	private void applySnapshot(KeywordRanking row) {
		MarketingPeriod previousPeriod = periodOf(row).previous();
		var previous = rankingRepository.findByKeywordIdAndMonthAndYear(row.getKeyword().getId(),
				previousPeriod.month(), previousPeriod.year());
		Integer previousPosition = previous.map(KeywordRanking::getPosition).orElse(null);
		row.setPreviousPosition(previousPosition);
		row.setRankingChange(RankingChange.of(previous.isPresent(), previousPosition, row.getPosition()).value());
	}

	private void auditRecorded(KeywordRanking saved, Long keywordId, MarketingPeriod period, AuthenticatedUser actor,
			ClientInfo client) {
		Map<String, Object> details = new HashMap<>();
		details.put("keywordId", keywordId);
		details.put("month", period.month());
		details.put("year", period.year());
		details.put("position", saved.getPosition());
		auditService.record(AuditAction.SEO_RANKING_RECORDED, actor.id(), ENTITY, saved.getId(), details, client);
	}

	private SeoKeyword loadKeyword(Long id) {
		return keywordRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("SEO_KEYWORD_NOT_FOUND", "Keyword not found"));
	}

	private static void requireTracked(SeoKeyword keyword) {
		if (keyword.getStatus() == KeywordStatus.ARCHIVED) {
			throw ApiException.badRequest("KEYWORD_ARCHIVED",
					"\"" + keyword.getKeyword() + "\" is archived; restore it before recording rankings");
		}
	}

	static ApiException alreadyRecorded(MarketingPeriod period) {
		return ApiException.conflict("RANKING_EXISTS",
				period.label() + " is already recorded for this keyword. Correct that month instead.");
	}

	private static MarketingPeriod periodOf(KeywordRanking row) {
		return new MarketingPeriod(row.getMonth(), row.getYear());
	}

	private static String positionText(Integer position) {
		return position == null ? "NR" : position.toString();
	}

}
