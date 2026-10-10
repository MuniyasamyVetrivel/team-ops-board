package com.teamops.marketing.seo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.web.ClientInfo;
import com.teamops.marketing.common.RankingStatus;
import com.teamops.marketing.seo.dto.RankingDtos.CorrectRanking;
import com.teamops.marketing.seo.dto.RankingDtos.MonthlyEntry;
import com.teamops.marketing.seo.dto.RankingDtos.RecordMonthly;
import com.teamops.marketing.seo.dto.RankingDtos.RecordRanking;
import com.teamops.marketing.seo.entity.KeywordRanking;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.seo.repository.KeywordRankingRepository;
import com.teamops.marketing.seo.repository.SeoKeywordRepository;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.marketing.seo.repository.SeoRankingQuery;
import com.teamops.marketing.seo.repository.SeoRankingTableQuery;
import com.teamops.support.SliceAuth;
import com.teamops.user.repository.UserRepository;

/**
 * Monthly ranking history is insert-only (brief sections 25 and 56): a recorded month is never recorded again, other
 * months are never overwritten, and a correction changes only its own month and is audited. Without a database;
 * {@code RankingFlowIT} covers the same rules against MySQL.
 */
class KeywordRankingServiceTest {

	/** 10 October 2026 in Asia/Kolkata: October is the current month, September still correctable. */
	private static final Instant NOW = Instant.parse("2026-10-10T06:00:00Z");

	private static final long KEYWORD_ID = 21L;

	private final SeoKeywordRepository keywordRepository = mock(SeoKeywordRepository.class);

	private final KeywordRankingRepository rankingRepository = mock(KeywordRankingRepository.class);

	private final AuditService auditService = mock(AuditService.class);

	private KeywordRankingService service;

	private SeoKeyword keyword;

	@BeforeEach
	void setUp() {
		service = new KeywordRankingService(keywordRepository, mock(SeoPageRepository.class), rankingRepository,
				mock(SeoRankingQuery.class), mock(SeoRankingTableQuery.class), mock(UserRepository.class), auditService,
				new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata"));
		SeoPage page = new SeoPage();
		ReflectionTestUtils.setField(page, "id", 3L);
		page.setTitle("SAP Testing Services");
		page.setUrl("/services/sap-testing");
		keyword = new SeoKeyword();
		ReflectionTestUtils.setField(keyword, "id", KEYWORD_ID);
		keyword.setKeyword("SAP Testing Services");
		keyword.setPage(page);
		when(keywordRepository.findDetailedById(KEYWORD_ID)).thenReturn(Optional.of(keyword));
		when(keywordRepository.findByIdIn(any())).thenReturn(List.of(keyword));
		when(rankingRepository.findByKeywordIdAndMonthAndYear(anyLong(), any(), any())).thenReturn(Optional.empty());
		when(rankingRepository.save(any())).thenAnswer(call -> call.getArgument(0));
	}

	private KeywordRanking ranking(long id, int month, Integer position) {
		KeywordRanking row = new KeywordRanking();
		ReflectionTestUtils.setField(row, "id", id);
		row.setKeyword(keyword);
		row.setPage(keyword.getPage());
		row.setMonth(month);
		row.setYear(2026);
		row.setPosition(position);
		row.setVersion(0);
		return row;
	}

	private void stored(KeywordRanking row) {
		when(rankingRepository.findByKeywordIdAndMonthAndYear(KEYWORD_ID, row.getMonth(), row.getYear()))
			.thenReturn(Optional.of(row));
		when(rankingRepository.findDetailedById(row.getId())).thenReturn(Optional.of(row));
	}

	@Test
	void recordingANewMonthAddsARowAndSnapshotsTheMonthBefore() {
		KeywordRanking september = ranking(1L, 9, 12);
		stored(september);

		service.record(KEYWORD_ID, new RecordRanking(10, 2026, 7, 1_300, null), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown());

		ArgumentCaptor<KeywordRanking> saved = ArgumentCaptor.forClass(KeywordRanking.class);
		verify(rankingRepository).save(saved.capture());
		KeywordRanking october = saved.getValue();
		assertThat(october).isNotSameAs(september);
		assertThat(october.getMonth()).isEqualTo(10);
		assertThat(october.getPosition()).isEqualTo(7);
		// The brief's example: 12 → 7 is +5 positions.
		assertThat(october.getPreviousPosition()).isEqualTo(12);
		assertThat(october.getRankingChange()).isEqualTo(5);
		assertThat(september.getPosition()).as("September is never overwritten").isEqualTo(12);
		verify(auditService).record(eq(AuditAction.SEO_RANKING_RECORDED), eq(1L), any(), any(), any(), any());
	}

	@Test
	void aMonthThatIsAlreadyRecordedCannotBeRecordedAgain() {
		KeywordRanking october = ranking(2L, 10, 7);
		stored(october);

		assertError(() -> service.record(KEYWORD_ID, new RecordRanking(10, 2026, 3, null, null), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown()), HttpStatus.CONFLICT, "RANKING_EXISTS");

		assertThat(october.getPosition()).isEqualTo(7);
		verify(rankingRepository, never()).save(any());
		verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
	}

	@Test
	void theMonthlyUpdateIsAllOrNothingWhenAnyKeywordIsAlreadyRecorded() {
		when(rankingRepository.findRecordedKeywordIds(List.of(KEYWORD_ID), 10, 2026)).thenReturn(List.of(KEYWORD_ID));

		assertError(() -> service.recordMonthly(new RecordMonthly(10, 2026, List.of(new MonthlyEntry(KEYWORD_ID, 5, null,
				null))), SliceAuth.SUPER_ADMIN, ClientInfo.unknown()), HttpStatus.CONFLICT, "RANKING_EXISTS");

		verify(rankingRepository, never()).save(any());
	}

	@Test
	void notRankedIsStoredAsNullAndFutureMonthsAreRejected() {
		service.record(KEYWORD_ID, new RecordRanking(10, 2026, null, null, null), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown());
		ArgumentCaptor<KeywordRanking> saved = ArgumentCaptor.forClass(KeywordRanking.class);
		verify(rankingRepository).save(saved.capture());
		assertThat(saved.getValue().getPosition()).isNull();
		assertThat(RankingStatus.of(saved.getValue().getPosition())).isEqualTo(RankingStatus.NOT_RANKED);

		assertThatThrownBy(() -> service.record(KEYWORD_ID, new RecordRanking(11, 2026, 4, null, null),
				SliceAuth.SUPER_ADMIN, ClientInfo.unknown()))
			.isInstanceOf(ApiException.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	void aCorrectionChangesOnlyItsOwnMonthAndIsAuditedWithBeforeAndAfter() {
		KeywordRanking september = ranking(1L, 9, 12);
		KeywordRanking october = ranking(2L, 10, 7);
		october.setPreviousPosition(12);
		october.setRankingChange(5);
		stored(september);
		stored(october);

		service.correct(1L, new CorrectRanking(0, 10, null, null), SliceAuth.EMPLOYEE, ClientInfo.unknown());

		assertThat(september.getPosition()).isEqualTo(10);
		assertThat(october.getPosition()).as("the next month's own position stays").isEqualTo(7);
		assertThat(october.getPreviousPosition()).as("its snapshot follows the correction").isEqualTo(10);
		assertThat(october.getRankingChange()).isEqualTo(3);
		ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
		verify(auditService).record(eq(AuditAction.SEO_RANKING_CORRECTED), eq(4L), any(), eq(1L), details.capture(),
				any());
		assertThat((Map<String, Object>) details.getValue().get("changes")).containsKey("position");
		assertThat(details.getValue()).doesNotContainKey("closedMonth");
	}

	@Test
	void aCorrectionThatChangesNothingIsNotAudited() {
		stored(ranking(2L, 10, 7));

		service.correct(2L, new CorrectRanking(0, 7, null, null), SliceAuth.EMPLOYEE, ClientInfo.unknown());

		verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
	}

	@Test
	void closedMonthsAreLockedExceptForASuperAdminWhoseOverrideIsFlagged() {
		KeywordRanking august = ranking(3L, 8, 18);
		stored(august);

		assertError(() -> service.correct(3L, new CorrectRanking(0, 15, null, null), SliceAuth.EMPLOYEE,
				ClientInfo.unknown()), HttpStatus.CONFLICT, "MONTH_LOCKED");
		assertThat(august.getPosition()).isEqualTo(18);

		service.correct(3L, new CorrectRanking(0, 15, null, null), SliceAuth.SUPER_ADMIN, ClientInfo.unknown());
		assertThat(august.getPosition()).isEqualTo(15);
		verify(auditService).record(eq(AuditAction.SEO_RANKING_CORRECTED), eq(1L), any(), eq(3L),
				eq(Map.of("changes", Map.of("position", Map.of("from", 18, "to", 15)), "keywordId", KEYWORD_ID,
						"month", 8, "year", 2026, "closedMonth", true)),
				any());
	}

	@Test
	void aStaleVersionIsRejected() {
		stored(ranking(2L, 10, 7));

		assertError(() -> service.correct(2L, new CorrectRanking(5, 6, null, null), SliceAuth.EMPLOYEE,
				ClientInfo.unknown()), HttpStatus.CONFLICT, "STALE_UPDATE");
	}

	private static void assertError(ThrowingCallable call, HttpStatus status, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

}
