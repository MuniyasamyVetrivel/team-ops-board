package com.teamops.marketing.seo.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.entity.DepartmentStatus;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.seo.dto.SeoDtos.CreateKeyword;
import com.teamops.marketing.seo.dto.SeoDtos.CreatePage;
import com.teamops.marketing.seo.dto.SeoDtos.KeywordItem;
import com.teamops.marketing.seo.dto.SeoDtos.PageDetail;
import com.teamops.marketing.seo.dto.SeoDtos.PageListItem;
import com.teamops.marketing.seo.dto.SeoDtos.PageRef;
import com.teamops.marketing.seo.dto.SeoDtos.SeoPermissions;
import com.teamops.marketing.seo.dto.SeoDtos.UpdateKeyword;
import com.teamops.marketing.seo.dto.SeoDtos.UpdatePage;
import com.teamops.marketing.seo.entity.Device;
import com.teamops.marketing.seo.entity.KeywordStatus;
import com.teamops.marketing.seo.entity.PageStatus;
import com.teamops.marketing.seo.entity.PageType;
import com.teamops.marketing.seo.entity.SearchEngine;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.seo.repository.KeywordRankingRepository;
import com.teamops.marketing.seo.repository.SeoKeywordRepository;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.marketing.seo.repository.SeoRankingQuery;
import com.teamops.marketing.seo.repository.SeoSpecifications;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * SEO pages and their keywords (brief sections 23, 24 and 28). Positions, ranking statuses and page statistics are
 * computed from the ranking history for the requested month (default: the current business month). SEO data is shared
 * across the Digital Marketing team: SEO_VIEW reads everything and SEO_EDIT changes everything.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeoService {

	public static final String SEO_EDIT = "SEO_EDIT";

	static final String DEFAULT_LOCATION = "India";

	private static final String PAGE = "SEO_PAGE";

	private static final String KEYWORD = "SEO_KEYWORD";

	private final SeoPageRepository pageRepository;

	private final SeoKeywordRepository keywordRepository;

	private final KeywordRankingRepository rankingRepository;

	private final SeoRankingQuery rankingQuery;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	// --- pages ----------------------------------------------------------------------------------------------

	public PageResponse<PageListItem> searchPages(String search, Set<PageType> types, Set<PageStatus> statuses,
			Long ownerId, Long departmentId, MarketingPeriod period, Pageable pageable) {
		var spec = SeoSpecifications.pageMatches(search)
			.and(SeoSpecifications.pageTypeIn(types))
			.and(SeoSpecifications.pageStatusIn(statuses))
			.and(SeoSpecifications.pageOwner(ownerId))
			.and(SeoSpecifications.pageDepartment(departmentId));
		Page<SeoPage> page = pageRepository.findAll(spec, pageable);
		Map<Long, SeoStats> stats = statsByPage(page.getContent().stream().map(SeoPage::getId).toList(), period);
		return PageResponse.of(page.map(p -> PageListItem.of(p, stats.getOrDefault(p.getId(), SeoStats.EMPTY))));
	}

	/** Pages that can take keywords, for pickers and filters. */
	public List<PageRef> pageOptions() {
		return pageRepository.findByStatusNotOrderByTitleAsc(PageStatus.ARCHIVED).stream().map(PageRef::of).toList();
	}

	public PageDetail getPage(Long id, MarketingPeriod period, AuthenticatedUser actor) {
		return toDetail(loadPage(id), period, actor);
	}

	@Transactional
	public PageDetail createPage(CreatePage request, AuthenticatedUser actor, ClientInfo client) {
		String url = request.url().trim();
		if (pageRepository.existsByUrl(url)) {
			throw duplicateUrl();
		}
		SeoPage page = new SeoPage();
		page.setUrl(url);
		page.setTitle(request.title().trim());
		page.setPageType(request.pageType());
		page.setPrimaryKeyword(normalize(request.primaryKeyword()));
		page.setDepartment(resolveDepartment(request.departmentId()));
		page.setOwner(resolveOwner(request.ownerId()));
		SeoPage saved = pageRepository.save(page);
		auditService.record(AuditAction.SEO_PAGE_CREATED, actor.id(), PAGE, saved.getId(),
				Map.of("url", saved.getUrl(), "title", saved.getTitle()), client);
		return toDetail(saved, period(null, null), actor);
	}

	@Transactional
	public PageDetail updatePage(Long id, UpdatePage request, AuthenticatedUser actor, ClientInfo client) {
		SeoPage page = loadPage(id);
		checkVersion(page.getVersion(), request.version(), "page");
		String url = request.url().trim();
		if (!url.equals(page.getUrl()) && pageRepository.existsByUrlAndIdNot(url, id)) {
			throw duplicateUrl();
		}
		Department department = page.getDepartment().getId().equals(request.departmentId()) ? page.getDepartment()
				: resolveDepartment(request.departmentId());
		User owner = Objects.equals(userId(page.getOwner()), request.ownerId()) ? page.getOwner()
				: resolveOwner(request.ownerId());
		String primaryKeyword = normalize(request.primaryKeyword());
		AuditChanges changes = new AuditChanges().track("url", page.getUrl(), url)
			.track("title", page.getTitle(), request.title().trim())
			.track("pageType", page.getPageType(), request.pageType())
			.track("primaryKeyword", page.getPrimaryKeyword(), primaryKeyword)
			.track("departmentId", page.getDepartment().getId(), request.departmentId())
			.track("ownerId", userId(page.getOwner()), userId(owner))
			.track("status", page.getStatus(), request.status());
		page.setUrl(url);
		page.setTitle(request.title().trim());
		page.setPageType(request.pageType());
		page.setPrimaryKeyword(primaryKeyword);
		page.setDepartment(department);
		page.setOwner(owner);
		page.setStatus(request.status());
		pageRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.SEO_PAGE_UPDATED, actor.id(), PAGE, id, changes.toDetails(), client);
		}
		return toDetail(page, period(null, null), actor);
	}

	/** Only a page without keywords can be deleted; anything with history is archived instead. */
	@Transactional
	public void deletePage(Long id, AuthenticatedUser actor, ClientInfo client) {
		SeoPage page = loadPage(id);
		if (keywordRepository.countByPageId(id) > 0) {
			throw ApiException.conflict("PAGE_HAS_KEYWORDS",
					"This page has keywords. Archive it instead, so its ranking history is kept.");
		}
		pageRepository.delete(page);
		auditService.record(AuditAction.SEO_PAGE_DELETED, actor.id(), PAGE, id,
				Map.of("url", page.getUrl(), "title", page.getTitle()), client);
	}

	// --- keywords -------------------------------------------------------------------------------------------

	public PageResponse<KeywordItem> searchKeywords(String search, Long pageId, Long ownerId,
			Set<KeywordStatus> statuses, Device device, MarketingPeriod period, Pageable pageable) {
		var spec = SeoSpecifications.keywordMatches(search)
			.and(SeoSpecifications.keywordPage(pageId))
			.and(SeoSpecifications.keywordOwner(ownerId))
			.and(SeoSpecifications.keywordStatusIn(statuses))
			.and(SeoSpecifications.keywordDevice(device));
		Page<SeoKeyword> page = keywordRepository.findAll(spec, pageable);
		Map<Long, KeywordStanding> standings = standings(page.getContent().stream().map(SeoKeyword::getId).toList(),
				period);
		return PageResponse.of(page.map(k -> KeywordItem.of(k, standings.get(k.getId()))));
	}

	public KeywordItem getKeyword(Long id, MarketingPeriod period) {
		return toItem(loadKeyword(id), period);
	}

	@Transactional
	public KeywordItem createKeyword(CreateKeyword request, AuthenticatedUser actor, ClientInfo client) {
		SeoPage page = resolveOpenPage(request.pageId());
		SeoKeyword keyword = new SeoKeyword();
		keyword.setPage(page);
		keyword.setKeyword(normalize(request.keyword()));
		keyword.setSearchEngine(request.searchEngine() == null ? SearchEngine.GOOGLE : request.searchEngine());
		keyword.setLocation(location(request.location()));
		keyword.setDevice(request.device() == null ? Device.DESKTOP : request.device());
		checkUnique(page, keyword.getKeyword(), keyword.getSearchEngine(), keyword.getLocation(), keyword.getDevice(),
				null);
		keyword.setTargetPosition(request.targetPosition());
		keyword.setSearchVolume(request.searchVolume());
		keyword.setKeywordDifficulty(request.keywordDifficulty());
		keyword.setOwner(resolveOwner(request.ownerId()));
		SeoKeyword saved = keywordRepository.save(keyword);
		auditService.record(AuditAction.SEO_KEYWORD_CREATED, actor.id(), KEYWORD, saved.getId(),
				Map.of("keyword", saved.getKeyword(), "pageId", page.getId()), client);
		return toItem(saved, period(null, null));
	}

	@Transactional
	public KeywordItem updateKeyword(Long id, UpdateKeyword request, AuthenticatedUser actor, ClientInfo client) {
		SeoKeyword keyword = loadKeyword(id);
		checkVersion(keyword.getVersion(), request.version(), "keyword");
		SeoPage page = keyword.getPage().getId().equals(request.pageId()) ? keyword.getPage()
				: resolveOpenPage(request.pageId());
		User owner = Objects.equals(userId(keyword.getOwner()), request.ownerId()) ? keyword.getOwner()
				: resolveOwner(request.ownerId());
		String text = normalize(request.keyword());
		SearchEngine engine = request.searchEngine() == null ? SearchEngine.GOOGLE : request.searchEngine();
		String location = location(request.location());
		Device device = request.device() == null ? Device.DESKTOP : request.device();
		// Checked before the entity changes: the query would otherwise flush the edit first.
		checkUnique(page, text, engine, location, device, id);
		AuditChanges changes = new AuditChanges().track("pageId", keyword.getPage().getId(), page.getId())
			.track("keyword", keyword.getKeyword(), text)
			.track("searchEngine", keyword.getSearchEngine(), engine)
			.track("location", keyword.getLocation(), location)
			.track("device", keyword.getDevice(), device)
			.track("targetPosition", keyword.getTargetPosition(), request.targetPosition())
			.track("searchVolume", keyword.getSearchVolume(), request.searchVolume())
			.track("keywordDifficulty", keyword.getKeywordDifficulty(), request.keywordDifficulty())
			.track("ownerId", userId(keyword.getOwner()), userId(owner))
			.track("status", keyword.getStatus(), request.status());
		keyword.setPage(page);
		keyword.setKeyword(text);
		keyword.setSearchEngine(engine);
		keyword.setLocation(location);
		keyword.setDevice(device);
		keyword.setTargetPosition(request.targetPosition());
		keyword.setSearchVolume(request.searchVolume());
		keyword.setKeywordDifficulty(request.keywordDifficulty());
		keyword.setOwner(owner);
		keyword.setStatus(request.status());
		keywordRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.SEO_KEYWORD_UPDATED, actor.id(), KEYWORD, id, changes.toDetails(), client);
		}
		return toItem(keyword, period(null, null));
	}

	/** Only a keyword that was never ranked can be deleted; history is never thrown away. */
	@Transactional
	public void deleteKeyword(Long id, AuthenticatedUser actor, ClientInfo client) {
		SeoKeyword keyword = loadKeyword(id);
		if (rankingRepository.existsByKeywordId(id)) {
			throw ApiException.conflict("KEYWORD_HAS_HISTORY",
					"This keyword has ranking history. Archive it instead, so the history is kept.");
		}
		keywordRepository.delete(keyword);
		auditService.record(AuditAction.SEO_KEYWORD_DELETED, actor.id(), KEYWORD, id,
				Map.of("keyword", keyword.getKeyword(), "pageId", keyword.getPage().getId()), client);
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private PageDetail toDetail(SeoPage page, MarketingPeriod period, AuthenticatedUser actor) {
		MarketingPeriod previous = period.previous();
		List<Long> ids = List.of(page.getId());
		return new PageDetail(page.getId(), page.getUrl(), page.getTitle(), page.getPageType(),
				page.getPrimaryKeyword(), DepartmentSummary.of(page.getDepartment()), UserSummary.of(page.getOwner()),
				page.getStatus(), Period.of(period), statsByPage(ids, period).getOrDefault(page.getId(), SeoStats.EMPTY),
				Period.of(previous), statsByPage(ids, previous).getOrDefault(page.getId(), SeoStats.EMPTY),
				keywordRepository.countByPageId(page.getId()), page.getVersion(), page.getCreatedAt(),
				page.getUpdatedAt(), new SeoPermissions(actor.hasPermission(SEO_EDIT)));
	}

	private KeywordItem toItem(SeoKeyword keyword, MarketingPeriod period) {
		return KeywordItem.of(keyword, standings(List.of(keyword.getId()), period).get(keyword.getId()));
	}

	/** Statistics per page over its keywords that are not archived. */
	private Map<Long, SeoStats> statsByPage(List<Long> pageIds, MarketingPeriod period) {
		Map<Long, List<KeywordStanding>> byPage = rankingQuery.forPages(pageIds, period)
			.stream()
			.filter(row -> row.status() != KeywordStatus.ARCHIVED)
			.collect(Collectors.groupingBy(SeoRankingQuery.Row::pageId,
					Collectors.mapping(SeoRankingQuery.Row::standing, Collectors.toList())));
		Map<Long, SeoStats> stats = new HashMap<>();
		byPage.forEach((pageId, standings) -> stats.put(pageId, SeoStats.of(standings)));
		return stats;
	}

	private Map<Long, KeywordStanding> standings(List<Long> keywordIds, MarketingPeriod period) {
		Map<Long, KeywordStanding> standings = new HashMap<>();
		rankingQuery.forKeywords(keywordIds, period).forEach(row -> standings.put(row.keywordId(), row.standing()));
		keywordIds.forEach(id -> standings.putIfAbsent(id, KeywordStanding.unrecorded()));
		return standings;
	}

	private SeoPage loadPage(Long id) {
		return pageRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("SEO_PAGE_NOT_FOUND", "Page not found"));
	}

	private SeoKeyword loadKeyword(Long id) {
		return keywordRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("SEO_KEYWORD_NOT_FOUND", "Keyword not found"));
	}

	private SeoPage resolveOpenPage(Long pageId) {
		SeoPage page = pageRepository.findById(pageId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_PAGE", "Page not found"));
		if (page.getStatus() == PageStatus.ARCHIVED) {
			throw ApiException.badRequest("PAGE_ARCHIVED", "Keywords cannot be added to an archived page");
		}
		return page;
	}

	private void checkUnique(SeoPage page, String keyword, SearchEngine engine, String location, Device device,
			Long excludeId) {
		if (keywordRepository.existsIdentity(page.getId(), keyword, engine, location, device, excludeId)) {
			throw ApiException.conflict("DUPLICATE_KEYWORD",
					"This page already tracks that keyword for the same search engine, location and device");
		}
	}

	private Department resolveDepartment(Long departmentId) {
		Department department = departmentRepository.findById(departmentId)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Department not found"));
		if (department.getStatus() != DepartmentStatus.ACTIVE) {
			throw ApiException.badRequest("DEPARTMENT_INACTIVE", "Choose an active department");
		}
		return department;
	}

	/** Owners must be active Digital Marketing users, so they can open what they own. */
	private User resolveOwner(Long ownerId) {
		if (ownerId == null) {
			return null;
		}
		return userRepository.findActiveWithPermission(MarketingContextService.MARKETING_VIEW, UserStatus.ACTIVE)
			.stream()
			.filter(user -> user.getId().equals(ownerId))
			.findFirst()
			.orElseThrow(() -> ApiException.badRequest("INVALID_OWNER",
					"The owner must be an active user with access to Digital Marketing"));
	}

	private static void checkVersion(Integer current, Integer requested, String noun) {
		if (!Objects.equals(current, requested)) {
			throw ApiException.conflict("STALE_UPDATE",
					"Someone else changed this " + noun + " just now. Reload and try again.");
		}
	}

	private static ApiException duplicateUrl() {
		return ApiException.conflict("DUPLICATE_URL", "A page with this URL already exists");
	}

	/** Trims and collapses inner whitespace; blank becomes {@code null}. */
	static String normalize(String value) {
		return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", " ") : null;
	}

	private static String location(String value) {
		String location = normalize(value);
		return location == null ? DEFAULT_LOCATION : location;
	}

	private static Long userId(User user) {
		return user == null ? null : user.getId();
	}

}
