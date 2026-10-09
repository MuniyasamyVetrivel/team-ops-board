package com.teamops.marketing.content.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.common.MarketingMonths;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.dto.ContentDtos.BlogTarget;
import com.teamops.marketing.content.dto.ContentDtos.ChangeStatus;
import com.teamops.marketing.content.dto.ContentDtos.ContentItemDto;
import com.teamops.marketing.content.dto.ContentDtos.ContentSummary;
import com.teamops.marketing.content.dto.ContentDtos.ContentTrend;
import com.teamops.marketing.content.dto.ContentDtos.KeywordRef;
import com.teamops.marketing.content.dto.ContentDtos.MonthFigures;
import com.teamops.marketing.content.dto.ContentDtos.PageRef;
import com.teamops.marketing.content.dto.ContentDtos.SaveContent;
import com.teamops.marketing.content.dto.ContentDtos.StatusCount;
import com.teamops.marketing.content.dto.ContentDtos.TopContent;
import com.teamops.marketing.content.dto.ContentDtos.TrendMonth;
import com.teamops.marketing.content.dto.ContentDtos.TypeCount;
import com.teamops.marketing.content.entity.ContentItem;
import com.teamops.marketing.content.entity.ContentStatus;
import com.teamops.marketing.content.entity.ContentType;
import com.teamops.marketing.content.repository.ContentItemRepository;
import com.teamops.marketing.content.repository.ContentQuery;
import com.teamops.marketing.content.service.ContentRules.Dates;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.lead.repository.MarketingLeadRepository;
import com.teamops.marketing.seo.entity.SeoKeyword;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.seo.repository.SeoKeywordRepository;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.MarketingTarget;
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.marketing.target.repository.MarketingTargetRepository;
import com.teamops.marketing.target.repository.TargetTypeRepository;
import com.teamops.marketing.target.service.TargetService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Content & Blog (brief sections 47–48): CONTENT_VIEW reads, CONTENT_EDIT changes; shared across the Digital Marketing
 * team. Live content (PUBLISHED or UPDATED) counts in the month of its publication date, and a blog towards the
 * monthly blog target, so whether and when an item counts can only change while the months involved are open (the
 * current or previous business month, any past month for a Super Admin, {@link MarketingMonths}). Content that leads
 * name stays published, no later than its first lead, and cannot be deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

	public static final String TARGET_VIEW = "TARGET_VIEW";

	public static final int EXPORT_LIMIT = 10_000;

	public static final int DEFAULT_TREND_MONTHS = 12;

	public static final int MAX_TREND_MONTHS = 36;

	public static final int TOP_CONTENT = 10;

	static final String ENTITY = "CONTENT_ITEM";

	private final ContentItemRepository contentRepository;

	private final ContentQuery contentQuery;

	private final MarketingLeadRepository leadRepository;

	private final SeoKeywordRepository keywordRepository;

	private final SeoPageRepository pageRepository;

	private final UserRepository userRepository;

	private final TargetService targetService;

	private final TargetTypeRepository typeRepository;

	private final MarketingTargetRepository targetRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	/** Which date a month filter applies to. */
	public enum DateField {
		PLANNED, PUBLISHED
	}

	/**
	 * List filters; every field is optional. {@code period} limits the list to items planned or published (or
	 * refreshed) in that month, or only by the {@code dateField} given.
	 */
	public record ContentFilter(String search, Set<ContentStatus> statuses, Set<ContentType> types, Long ownerId,
			Long authorId, MarketingPeriod period, DateField dateField) {
	}

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	// --- reading ----------------------------------------------------------------------------------------------

	public PageResponse<ContentItemDto> search(ContentFilter filter, Pageable pageable, AuthenticatedUser viewer) {
		Page<ContentItem> page = contentRepository.findAll(spec(filter), pageable);
		Map<Long, Long> leads = contentQuery.leadCounts(page.getContent().stream().map(ContentItem::getId).toList());
		LocalDate today = calendar.today();
		return PageResponse.of(page.map(c -> toDto(c, leads.getOrDefault(c.getId(), 0L), today, viewer)));
	}

	public ContentItemDto get(Long id, AuthenticatedUser viewer) {
		return toDto(load(id), viewer);
	}

	/**
	 * Brief section 48: the month's blogs planned and published against the comparison month, the blog target with
	 * what remains (and the Blog Leads target) for TARGET_VIEW holders, content published by type, today's pipeline
	 * and the content that brought in the most leads.
	 */
	public ContentSummary summary(MarketingPeriod period, MarketingPeriod comparison, Long ownerId,
			AuthenticatedUser viewer) {
		ContentQuery.MonthFigures current = contentQuery.monthly(period, period, ownerId)
			.getOrDefault(period, ContentQuery.MonthFigures.ZERO);
		ContentQuery.MonthFigures before = contentQuery.monthly(comparison, comparison, ownerId)
			.getOrDefault(comparison, ContentQuery.MonthFigures.ZERO);

		boolean targetsVisible = viewer.hasPermission(TARGET_VIEW);
		BlogTarget blogTarget = null;
		TargetItem blogLeadsTarget = null;
		if (targetsVisible) {
			TargetType blogs = blogType();
			TargetType blogLeads = typeRepository.findAllByOrderByPositionAscNameAsc()
				.stream()
				.filter(t -> t.getActualSource() == ActualSource.LEADS_BY_SOURCE && t.getLeadSourceFilter() == LeadSource.BLOG)
				.findFirst()
				.orElse(null);
			for (TargetItem item : targetService.monthly(period, null, null, viewer).targets()) {
				if (blogs != null && item.type().id().equals(blogs.getId()) && blogTarget == null) {
					blogTarget = new BlogTarget(item.targetValue(),
							ContentRules.remaining(item.targetValue(), current.publishedBlogs()), item);
				}
				if (blogLeads != null && item.type().id().equals(blogLeads.getId()) && blogLeadsTarget == null) {
					blogLeadsTarget = item;
				}
			}
		}

		List<TypeCount> byType = contentQuery.publishedByType(period, ownerId)
			.entrySet()
			.stream()
			.sorted(Map.Entry.<ContentType, Long>comparingByValue().reversed())
			.map(e -> new TypeCount(e.getKey(), e.getValue()))
			.toList();
		Map<ContentStatus, Long> statuses = contentQuery.byStatus(ownerId);
		List<StatusCount> pipeline = new ArrayList<>();
		for (ContentStatus status : ContentStatus.values()) {
			pipeline.add(new StatusCount(status, statuses.getOrDefault(status, 0L)));
		}
		List<TopContent> top = contentQuery.topByLeads(period, ownerId, TOP_CONTENT)
			.stream()
			.map(c -> new TopContent(c.id(), c.title(), c.contentType(), c.url(), c.organicTraffic(), c.ctaClicks(),
					c.leads()))
			.toList();
		return new ContentSummary(figures(period, current), figures(comparison, before), targetsVisible, blogTarget,
				blogLeadsTarget, byType, pipeline, top);
	}

	/**
	 * The monthly history for {@code months} months (default 12, at most 36) ending with {@code end}, oldest first:
	 * each month's figures with its blog target, what was left and the achievement (planned months have neither).
	 */
	public ContentTrend trend(MarketingPeriod end, Integer months, Long ownerId, AuthenticatedUser viewer) {
		int count = months == null ? DEFAULT_TREND_MONTHS : Math.clamp(months, 1, MAX_TREND_MONTHS);
		MarketingPeriod start = end.plusMonths(-(count - 1));
		Map<MarketingPeriod, ContentQuery.MonthFigures> figures = contentQuery.monthly(start, end, ownerId);
		boolean targetsVisible = viewer.hasPermission(TARGET_VIEW);
		Map<MarketingPeriod, BigDecimal> targets = new HashMap<>();
		TargetType type = targetsVisible ? blogType() : null;
		if (type != null) {
			for (MarketingTarget t : targetRepository.findForTrend(type.getId(), key(start), key(end))) {
				targets.put(new MarketingPeriod(t.getMonth(), t.getYear()), t.getTargetValue());
			}
		}
		LocalDate today = calendar.today();
		List<TrendMonth> points = new ArrayList<>();
		for (MarketingPeriod p = start; !p.firstDay().isAfter(end.firstDay()); p = p.next()) {
			ContentQuery.MonthFigures f = figures.getOrDefault(p, ContentQuery.MonthFigures.ZERO);
			BigDecimal target = targets.get(p);
			boolean planned = MarketingMonths.isFuture(p, today);
			points.add(new TrendMonth(figures(p, f), target,
					target == null || planned ? null : ContentRules.remaining(target, f.publishedBlogs()),
					target == null || planned ? null : MarketingMath.percent(f.publishedBlogs(), target)));
		}
		return new ContentTrend(targetsVisible, points);
	}

	/** The content table as CSV (same filters, newest first), at most {@link #EXPORT_LIMIT} rows. */
	public byte[] export(ContentFilter filter) {
		List<ContentItem> items = contentRepository
			.findAll(spec(filter), PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))))
			.getContent();
		Map<Long, Long> leads = contentQuery.leadCounts(items.stream().map(ContentItem::getId).toList());
		List<List<String>> rows = items.stream()
			.map(c -> List.of(c.getTitle(), c.getContentType().name(), c.getStatus().name(), text(c.getUrl()),
					c.getAuthor() == null ? "" : c.getAuthor().getFullName(),
					c.getOwner() == null ? "" : c.getOwner().getFullName(), text(c.getPlannedDate()),
					text(c.getPublicationDate()), text(c.getRefreshedDate()), text(c.getTargetKeywordText()),
					c.getTargetPage() == null ? "" : c.getTargetPage().getUrl(), text(c.getOrganicTraffic()),
					text(c.getCtaClicks()), String.valueOf(leads.getOrDefault(c.getId(), 0L)), text(c.getNotes())))
			.toList();
		return CsvWriter.write(List.of("Title", "Type", "Status", "URL", "Author", "Owner", "Planned", "Published",
				"Refreshed", "Target keyword", "Target page", "Organic traffic", "CTA clicks", "Leads", "Notes"), rows);
	}

	// --- changes ----------------------------------------------------------------------------------------------

	@Transactional
	public ContentItemDto create(SaveContent request, AuthenticatedUser actor, ClientInfo client) {
		ContentStatus status = request.status() == null ? ContentStatus.IDEA : request.status();
		Dates dates = new Dates(request.publicationDate(), request.refreshedDate());
		String url = normaliseUrl(request.url(), null);
		ContentRules.check(status, dates, url, calendar.today());
		requireCountable(null, null, Dates.NONE, status, request.contentType(), dates, actor);
		ContentItem item = new ContentItem();
		applyDetails(item, request, url, resolveUser(request.authorId(), "author"), resolveUser(request.ownerId(), "owner"));
		item.setStatus(status);
		item.setPublicationDate(dates.published());
		item.setRefreshedDate(dates.refreshed());
		item.setCreatedBy(userRepository.getReferenceById(actor.id()));
		ContentItem saved = contentRepository.save(item);
		auditService.record(AuditAction.CONTENT_CREATED, actor.id(), ENTITY, saved.getId(), createdDetails(saved), client);
		return toDto(saved, actor);
	}

	@Transactional
	public ContentItemDto update(Long id, SaveContent request, AuthenticatedUser actor, ClientInfo client) {
		ContentItem item = load(id);
		requireVersion(item, request.version());
		ContentStatus status = request.status() == null ? item.getStatus() : request.status();
		Dates before = new Dates(item.getPublicationDate(), item.getRefreshedDate());
		Dates dates = new Dates(request.publicationDate(), request.refreshedDate());
		String url = normaliseUrl(request.url(), id);
		ContentRules.check(status, dates, url, calendar.today());
		requireLeadsKept(item, status, dates);
		boolean closedMonth = requireCountable(item.getStatus(), item.getContentType(), before, status,
				request.contentType(), dates, actor);
		User author = Objects.equals(id(item.getAuthor()), request.authorId()) ? item.getAuthor()
				: resolveUser(request.authorId(), "author");
		User owner = Objects.equals(id(item.getOwner()), request.ownerId()) ? item.getOwner()
				: resolveUser(request.ownerId(), "owner");
		String keywordBefore = item.getTargetKeywordText();
		Long pageBefore = item.getTargetPage() == null ? null : item.getTargetPage().getId();
		AuditChanges changes = new AuditChanges().track("title", item.getTitle(), request.title().trim())
			.track("url", item.getUrl(), url)
			.track("contentType", item.getContentType(), request.contentType())
			.track("status", item.getStatus(), status)
			.track("plannedDate", item.getPlannedDate(), request.plannedDate())
			.track("publicationDate", before.published(), dates.published())
			.track("refreshedDate", before.refreshed(), dates.refreshed())
			.track("authorId", id(item.getAuthor()), id(author))
			.track("ownerId", id(item.getOwner()), id(owner))
			.track("organicTraffic", item.getOrganicTraffic(), request.organicTraffic())
			.track("ctaClicks", item.getCtaClicks(), request.ctaClicks());
		applyDetails(item, request, url, author, owner);
		changes.track("targetKeyword", keywordBefore, item.getTargetKeywordText())
			.track("targetPageId", pageBefore, item.getTargetPage() == null ? null : item.getTargetPage().getId());
		item.setStatus(status);
		item.setPublicationDate(dates.published());
		item.setRefreshedDate(dates.refreshed());
		contentRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.CONTENT_UPDATED, actor.id(), ENTITY, id, withClosedMonth(changes, closedMonth),
					client);
		}
		return toDto(item, actor);
	}

	/**
	 * Moves an item to a status ({@link ContentRules#moveTo}): publishing dates it {@code date} (today when omitted),
	 * updating dates the refresh, earlier stages clear the dates. The same rules apply; audited.
	 */
	@Transactional
	public ContentItemDto changeStatus(Long id, ChangeStatus request, AuthenticatedUser actor, ClientInfo client) {
		ContentItem item = load(id);
		requireVersion(item, request.version());
		ContentStatus from = item.getStatus();
		Dates before = new Dates(item.getPublicationDate(), item.getRefreshedDate());
		Dates dates = ContentRules.moveTo(request.status(), before, request.date() == null ? calendar.today() : request.date());
		if (from == request.status() && dates.equals(before)) {
			return toDto(item, actor);
		}
		ContentRules.check(request.status(), dates, item.getUrl(), calendar.today());
		requireLeadsKept(item, request.status(), dates);
		boolean closedMonth = requireCountable(from, item.getContentType(), before, request.status(),
				item.getContentType(), dates, actor);
		AuditChanges changes = new AuditChanges().track("status", from, request.status())
			.track("publicationDate", before.published(), dates.published())
			.track("refreshedDate", before.refreshed(), dates.refreshed());
		item.setStatus(request.status());
		item.setPublicationDate(dates.published());
		item.setRefreshedDate(dates.refreshed());
		contentRepository.flush();
		auditService.record(AuditAction.CONTENT_STATUS_CHANGED, actor.id(), ENTITY, id,
				withClosedMonth(changes, closedMonth), client);
		return toDto(item, actor);
	}

	/** Not while leads name it, and live content only while its month is open. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		ContentItem item = load(id);
		if (leadRepository.existsByContentItemId(id)) {
			throw ApiException.conflict("CONTENT_HAS_LEADS",
					"Leads name this content, so it cannot be deleted");
		}
		requireCountable(item.getStatus(), item.getContentType(), new Dates(item.getPublicationDate(), item.getRefreshedDate()),
				ContentStatus.IDEA, item.getContentType(), Dates.NONE, actor);
		contentRepository.delete(item);
		Map<String, Object> details = new HashMap<>();
		details.put("title", item.getTitle());
		details.put("status", item.getStatus());
		auditService.record(AuditAction.CONTENT_DELETED, actor.id(), ENTITY, id, details, client);
	}

	// --- helpers --------------------------------------------------------------------------------------------

	/**
	 * Where the item counts may only change in open months: its publication (whether it is live, its date and type)
	 * and its refresh. Each month involved, old and new, must be open for the actor.
	 *
	 * @return whether a closed month changed (only a Super Admin gets that far), for the audit
	 */
	private boolean requireCountable(ContentStatus fromStatus, ContentType fromType, Dates from, ContentStatus toStatus,
			ContentType toType, Dates to, AuthenticatedUser actor) {
		LocalDate today = calendar.today();
		boolean closed = false;
		LocalDate publishedBefore = fromStatus != null && fromStatus.isLive() ? from.published() : null;
		LocalDate publishedAfter = toStatus.isLive() ? to.published() : null;
		boolean typeMoved = publishedBefore != null && publishedAfter != null && fromType != toType;
		if (!Objects.equals(publishedBefore, publishedAfter) || typeMoved) {
			closed |= requireOpen(publishedBefore, today, actor, "published");
			closed |= requireOpen(publishedAfter, today, actor, "published");
		}
		if (!Objects.equals(from.refreshed(), to.refreshed())) {
			closed |= requireOpen(from.refreshed(), today, actor, "refreshed");
			closed |= requireOpen(to.refreshed(), today, actor, "refreshed");
		}
		return closed;
	}

	private static boolean requireOpen(LocalDate date, LocalDate today, AuthenticatedUser actor, String what) {
		if (date == null) {
			return false;
		}
		MarketingPeriod period = MarketingPeriod.of(date);
		if (!MarketingMonths.canCorrect(period, today, actor.isSuperAdmin())) {
			throw ApiException.conflict("MONTH_LOCKED", "This content counts as " + what + " in " + period.label()
					+ ", which is closed. Only the current and previous month can change.");
		}
		return !MarketingMonths.isOpen(period, today);
	}

	/** Leads only name published content, never dated before it: content with leads stays live and no later. */
	private void requireLeadsKept(ContentItem item, ContentStatus status, Dates dates) {
		LocalDate firstLead = leadRepository.findFirstLeadDateForContent(item.getId());
		if (firstLead == null) {
			return;
		}
		if (!status.isLive()) {
			throw ApiException.conflict("CONTENT_HAS_LEADS", "Leads name this content, so it stays published");
		}
		if (dates.published().isAfter(firstLead)) {
			throw ApiException.conflict("CONTENT_HAS_LEADS",
					"Leads came in from this content on " + firstLead + ", so it cannot be published after that");
		}
	}

	private ContentItemDto toDto(ContentItem item, AuthenticatedUser viewer) {
		long leads = contentQuery.leadCounts(List.of(item.getId())).getOrDefault(item.getId(), 0L);
		return toDto(item, leads, calendar.today(), viewer);
	}

	private static ContentItemDto toDto(ContentItem c, long leads, LocalDate today, AuthenticatedUser viewer) {
		boolean publicationLocked = c.getStatus().isLive() && c.getPublicationDate() != null
				&& !MarketingMonths.canCorrect(MarketingPeriod.of(c.getPublicationDate()), today, viewer.isSuperAdmin());
		boolean refreshLocked = c.getRefreshedDate() != null
				&& !MarketingMonths.canCorrect(MarketingPeriod.of(c.getRefreshedDate()), today, viewer.isSuperAdmin());
		SeoKeyword keyword = c.getTargetKeyword();
		SeoPage page = c.getTargetPage();
		return new ContentItemDto(c.getId(), c.getTitle(), c.getUrl(), c.getContentType(), c.getStatus(),
				UserSummary.of(c.getAuthor()), UserSummary.of(c.getOwner()), c.getPlannedDate(), c.getPublicationDate(),
				c.getRefreshedDate(), keyword == null ? null : new KeywordRef(keyword.getId(), keyword.getKeyword()),
				c.getTargetKeywordText(), page == null ? null : new PageRef(page.getId(), page.getTitle(), page.getUrl()),
				c.getOrganicTraffic(), c.getCtaClicks(), c.getNotes(), leads, publicationLocked, refreshLocked,
				UserSummary.of(c.getCreatedBy()), c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
	}

	private void applyDetails(ContentItem item, SaveContent request, String url, User author, User owner) {
		SeoKeyword keyword = request.targetKeywordId() == null ? null
				: keywordRepository.findById(request.targetKeywordId())
					.orElseThrow(() -> ApiException.badRequest("INVALID_KEYWORD", "The target keyword was not found"));
		SeoPage page = request.targetPageId() == null ? null
				: pageRepository.findById(request.targetPageId())
					.orElseThrow(() -> ApiException.badRequest("INVALID_PAGE", "The target page was not found"));
		String keywordText = trimToNull(request.targetKeywordText());
		item.setTitle(request.title().trim());
		item.setUrl(url);
		item.setContentType(request.contentType());
		item.setAuthor(author);
		item.setOwner(owner);
		item.setPlannedDate(request.plannedDate());
		item.setTargetKeyword(keyword);
		item.setTargetKeywordText(keywordText == null && keyword != null ? keyword.getKeyword() : keywordText);
		// A keyword belongs to a page: that page is the default target.
		item.setTargetPage(page == null && keyword != null ? keyword.getPage() : page);
		item.setOrganicTraffic(request.organicTraffic());
		item.setCtaClicks(request.ctaClicks());
		item.setNotes(trimToNull(request.notes()));
	}

	/** A URL (https://…) or a site path, unique among content items; null when blank. */
	private String normaliseUrl(String value, Long exceptId) {
		String url = trimToNull(value);
		if (url == null) {
			return null;
		}
		if (!isUrl(url)) {
			throw ApiException.badRequest("INVALID_URL", "The URL must be a full URL (https://…) or a path starting with /");
		}
		if (contentRepository.existsUrl(url, exceptId)) {
			throw ApiException.conflict("DUPLICATE_URL", "Another content item already has this URL");
		}
		return url;
	}

	static boolean isUrl(String value) {
		if (value.startsWith("/") && !value.startsWith("//") && !value.contains(" ")) {
			return true;
		}
		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
		}
		catch (URISyntaxException ex) {
			return false;
		}
	}

	/** Authors and owners must be active Digital Marketing users. */
	private User resolveUser(Long userId, String role) {
		if (userId == null) {
			return null;
		}
		return userRepository.findActiveWithPermission(MarketingContextService.MARKETING_VIEW, UserStatus.ACTIVE)
			.stream()
			.filter(user -> user.getId().equals(userId))
			.findFirst()
			.orElseThrow(() -> ApiException.badRequest("INVALID_" + role.toUpperCase(Locale.ROOT),
					"The " + role + " must be an active user with access to Digital Marketing"));
	}

	/** The blog target type: the first type whose actual counts published blogs. */
	private TargetType blogType() {
		return typeRepository.findAllByOrderByPositionAscNameAsc()
			.stream()
			.filter(t -> t.getActualSource() == ActualSource.BLOGS_PUBLISHED)
			.findFirst()
			.orElse(null);
	}

	private static MonthFigures figures(MarketingPeriod period, ContentQuery.MonthFigures f) {
		return new MonthFigures(Period.of(period), f.plannedBlogs(), f.publishedBlogs(), f.publishedAll(), f.refreshed(),
				f.leads());
	}

	private static Map<String, Object> createdDetails(ContentItem item) {
		Map<String, Object> details = new HashMap<>();
		details.put("title", item.getTitle());
		details.put("contentType", item.getContentType());
		details.put("status", item.getStatus());
		if (item.getPublicationDate() != null) {
			details.put("publicationDate", item.getPublicationDate().toString());
		}
		return details;
	}

	private static Map<String, Object> withClosedMonth(AuditChanges changes, boolean closedMonth) {
		Map<String, Object> details = new HashMap<>(changes.toDetails());
		if (closedMonth) {
			details.put("closedMonth", true);
		}
		return details;
	}

	private static void requireVersion(ContentItem item, Integer version) {
		if (version == null || !Objects.equals(item.getVersion(), version)) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this content just now. Reload and try again.");
		}
	}

	private ContentItem load(Long id) {
		return contentRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("CONTENT_NOT_FOUND", "Content item not found"));
	}

	private static Specification<ContentItem> spec(ContentFilter f) {
		Specification<ContentItem> spec = (root, query, cb) -> cb.conjunction();
		if (StringUtils.hasText(f.search())) {
			String pattern = "%" + f.search().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_") + "%";
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
					cb.like(cb.lower(root.get("url")), pattern, '\\'),
					cb.like(cb.lower(root.get("targetKeywordText")), pattern, '\\')));
		}
		if (f.statuses() != null && !f.statuses().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("status").in(f.statuses()));
		}
		if (f.types() != null && !f.types().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("contentType").in(f.types()));
		}
		if (f.ownerId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
		}
		if (f.authorId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("author").get("id"), f.authorId()));
		}
		if (f.period() != null) {
			LocalDate from = f.period().firstDay();
			LocalDate to = f.period().lastDay();
			List<String> fields = f.dateField() == null ? List.of("plannedDate", "publicationDate", "refreshedDate")
					: List.of(f.dateField() == DateField.PLANNED ? "plannedDate" : "publicationDate");
			spec = spec.and((root, query, cb) -> cb.or(fields.stream()
				.map(field -> cb.between(root.<LocalDate>get(field), from, to))
				.toArray(jakarta.persistence.criteria.Predicate[]::new)));
		}
		return spec;
	}

	private static int key(MarketingPeriod period) {
		return period.year() * 12 + period.month();
	}

	static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

	private static Long id(User user) {
		return user == null ? null : user.getId();
	}

}
