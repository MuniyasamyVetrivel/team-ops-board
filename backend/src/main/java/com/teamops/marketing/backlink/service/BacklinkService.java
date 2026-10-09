package com.teamops.marketing.backlink.service;

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
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.teamops.common.sequence.CodeGenerator;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.marketing.backlink.dto.BacklinkDtos.BacklinkItem;
import com.teamops.marketing.backlink.dto.BacklinkDtos.BacklinkSummary;
import com.teamops.marketing.backlink.dto.BacklinkDtos.BacklinkTrend;
import com.teamops.marketing.backlink.dto.BacklinkDtos.ChangeStatus;
import com.teamops.marketing.backlink.dto.BacklinkDtos.MonthActivity;
import com.teamops.marketing.backlink.dto.BacklinkDtos.MonthTarget;
import com.teamops.marketing.backlink.dto.BacklinkDtos.OwnerActivity;
import com.teamops.marketing.backlink.dto.BacklinkDtos.PageRef;
import com.teamops.marketing.backlink.dto.BacklinkDtos.SaveBacklink;
import com.teamops.marketing.backlink.dto.BacklinkDtos.StatusCount;
import com.teamops.marketing.backlink.dto.BacklinkDtos.TrendMonth;
import com.teamops.marketing.backlink.dto.BacklinkDtos.TypeCount;
import com.teamops.marketing.backlink.entity.Backlink;
import com.teamops.marketing.backlink.entity.BacklinkDates;
import com.teamops.marketing.backlink.entity.BacklinkOrigin;
import com.teamops.marketing.backlink.entity.BacklinkStatus;
import com.teamops.marketing.backlink.entity.BacklinkType;
import com.teamops.marketing.backlink.repository.BacklinkQuery;
import com.teamops.marketing.backlink.repository.BacklinkQuery.StageCounts;
import com.teamops.marketing.backlink.repository.BacklinkQuery.StageKind;
import com.teamops.marketing.backlink.repository.BacklinkRepository;
import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.common.MarketingMonths;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.seo.entity.SeoPage;
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
 * Backlinks (brief sections 45–46): BACKLINK_VIEW reads, BACKLINK_EDIT changes; shared across the Digital Marketing
 * team. Each stage a backlink reaches keeps its date ({@link BacklinkRules}) and counts in that month: submitted,
 * approved, live (the Backlinks target), rejected, lost. A stage date can only be set, moved or cleared while both
 * the old and the new month are open (the current or previous business month, any past month for a Super Admin,
 * {@link MarketingMonths}); it is checked per date, so a backlink submitted in a closed month can still go live today.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BacklinkService {

	public static final String BACKLINK_EDIT = "BACKLINK_EDIT";

	public static final String TARGET_VIEW = "TARGET_VIEW";

	public static final int EXPORT_LIMIT = 10_000;

	public static final int DEFAULT_TREND_MONTHS = 12;

	public static final int MAX_TREND_MONTHS = 36;

	static final String ENTITY = "BACKLINK";

	private final BacklinkRepository backlinkRepository;

	private final BacklinkQuery backlinkQuery;

	private final SeoPageRepository pageRepository;

	private final UserRepository userRepository;

	private final TargetService targetService;

	private final TargetTypeRepository typeRepository;

	private final MarketingTargetRepository targetRepository;

	private final CodeGenerator codeGenerator;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	/**
	 * List filters; every field is optional. {@code period} limits the list to backlinks with a stage in that month:
	 * the {@code stage} given, or any stage when it is null.
	 */
	public record BacklinkFilter(String search, Set<BacklinkStatus> statuses, Set<BacklinkType> types, Long ownerId,
			Long targetPageId, MarketingPeriod period, StageKind stage) {
	}

	/** A backlink's target, link and domain after resolving and normalising the request. */
	public record Placement(SeoPage page, String targetUrl, String linkUrl, String referringDomain) {
	}

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	// --- reading ----------------------------------------------------------------------------------------------

	public PageResponse<BacklinkItem> search(BacklinkFilter filter, Pageable pageable, AuthenticatedUser viewer) {
		LocalDate today = calendar.today();
		return PageResponse.of(backlinkRepository.findAll(spec(filter), pageable).map(b -> toItem(b, today, viewer)));
	}

	public BacklinkItem get(Long id, AuthenticatedUser viewer) {
		return toItem(load(id), calendar.today(), viewer);
	}

	/**
	 * Brief section 46: the month's submitted, approved, live, rejected and lost backlinks against the comparison
	 * month, the Backlinks target with what is still to be submitted (TARGET_VIEW only), live links by type, activity
	 * by owner, and today's pipeline.
	 */
	public BacklinkSummary summary(MarketingPeriod period, MarketingPeriod comparison, Long ownerId,
			AuthenticatedUser viewer) {
		StageCounts current = backlinkQuery.monthly(period, period, ownerId).getOrDefault(period, StageCounts.ZERO);
		StageCounts before = backlinkQuery.monthly(comparison, comparison, ownerId)
			.getOrDefault(comparison, StageCounts.ZERO);
		boolean targetsVisible = viewer.hasPermission(TARGET_VIEW);
		MonthTarget target = null;
		if (targetsVisible) {
			TargetType type = backlinkType();
			TargetItem item = type == null ? null
					: targetService.monthly(period, null, null, viewer)
						.targets()
						.stream()
						.filter(t -> t.type().id().equals(type.getId()))
						.findFirst()
						.orElse(null);
			if (item != null) {
				target = new MonthTarget(item.targetValue(), BacklinkRules.remaining(item.targetValue(), current.submitted()),
						item);
			}
		}

		Map<BacklinkType, Long> byType = backlinkQuery.liveByType(period, ownerId);
		List<TypeCount> liveByType = byType.entrySet()
			.stream()
			.sorted(Map.Entry.<BacklinkType, Long>comparingByValue().reversed())
			.map(e -> new TypeCount(e.getKey(), e.getValue()))
			.toList();

		List<BacklinkQuery.OwnerCounts> owners = backlinkQuery.byOwner(period)
			.stream()
			.filter(o -> ownerId == null || ownerId.equals(o.ownerId()))
			.toList();
		Map<Long, User> users = userRepository
			.findAllById(owners.stream().map(BacklinkQuery.OwnerCounts::ownerId).filter(Objects::nonNull).toList())
			.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));
		List<OwnerActivity> byOwner = owners.stream()
			.map(o -> new OwnerActivity(UserSummary.of(o.ownerId() == null ? null : users.get(o.ownerId())),
					o.counts().submitted(), o.counts().approved(), o.counts().live()))
			.toList();

		Map<BacklinkStatus, Long> statuses = backlinkQuery.byStatus(ownerId);
		List<StatusCount> pipeline = new ArrayList<>();
		for (BacklinkStatus status : BacklinkStatus.values()) {
			pipeline.add(new StatusCount(status, statuses.getOrDefault(status, 0L)));
		}
		return new BacklinkSummary(activity(period, current), activity(comparison, before), targetsVisible, target,
				liveByType, byOwner, pipeline);
	}

	/**
	 * The monthly history for {@code months} months (default 12, at most 36) ending with {@code end}, oldest first:
	 * each month's activity with its target, what was left to submit, and live links as a share of the target.
	 */
	public BacklinkTrend trend(MarketingPeriod end, Integer months, Long ownerId, AuthenticatedUser viewer) {
		int count = months == null ? DEFAULT_TREND_MONTHS : Math.clamp(months, 1, MAX_TREND_MONTHS);
		MarketingPeriod start = end.plusMonths(-(count - 1));
		Map<MarketingPeriod, StageCounts> counts = backlinkQuery.monthly(start, end, ownerId);
		boolean targetsVisible = viewer.hasPermission(TARGET_VIEW);
		Map<MarketingPeriod, BigDecimal> targets = new HashMap<>();
		TargetType type = targetsVisible ? backlinkType() : null;
		if (type != null) {
			for (MarketingTarget t : targetRepository.findForTrend(type.getId(), key(start), key(end))) {
				targets.put(new MarketingPeriod(t.getMonth(), t.getYear()), t.getTargetValue());
			}
		}
		LocalDate today = calendar.today();
		List<TrendMonth> points = new ArrayList<>();
		for (MarketingPeriod p = start; !p.firstDay().isAfter(end.firstDay()); p = p.next()) {
			StageCounts c = counts.getOrDefault(p, StageCounts.ZERO);
			BigDecimal target = targets.get(p);
			boolean planned = MarketingMonths.isFuture(p, today);
			points.add(new TrendMonth(activity(p, c), target,
					target == null || planned ? null : BacklinkRules.remaining(target, c.submitted()),
					target == null || planned ? null : MarketingMath.percent(c.live(), target)));
		}
		return new BacklinkTrend(targetsVisible, points);
	}

	/** The backlink table as CSV (same filters, newest first), at most {@link #EXPORT_LIMIT} rows. */
	public byte[] export(BacklinkFilter filter) {
		List<Backlink> backlinks = backlinkRepository
			.findAll(spec(filter), PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))))
			.getContent();
		List<List<String>> rows = backlinks.stream()
			.map(b -> List.of(b.getCode(), b.getReferringDomain(), text(b.getLinkUrl()), b.getTargetUrl(),
					b.getTargetPage() == null ? "" : b.getTargetPage().getTitle(), text(b.getAnchorText()),
					b.getLinkType().name(), b.getStatus().name(), text(b.getSubmittedDate()), text(b.getApprovedDate()),
					text(b.getLiveDate()), text(b.getRejectedDate()), text(b.getLostDate()),
					b.getDomainAuthority() == null ? "" : b.getDomainAuthority().toString(),
					b.getOwner() == null ? "" : b.getOwner().getFullName(), text(b.getNotes())))
			.toList();
		return CsvWriter.write(List.of("Backlink ID", "Referring domain", "Link URL", "Target URL", "Target page",
				"Anchor text", "Type", "Status", "Submitted", "Approved", "Live", "Rejected", "Lost", "Domain authority",
				"Owner", "Notes"), rows);
	}

	// --- changes ----------------------------------------------------------------------------------------------

	@Transactional
	public BacklinkItem create(SaveBacklink request, AuthenticatedUser actor, ClientInfo client) {
		BacklinkStatus status = request.status() == null ? BacklinkStatus.PROSPECTED : request.status();
		BacklinkDates dates = datesOf(request);
		Placement placement = resolvePlacement(request.targetPageId(), request.targetUrl(), request.linkUrl(),
				request.referringDomain(), null);
		checkStages(status, dates, placement.linkUrl(), BacklinkDates.NONE, actor);
		Backlink backlink = new Backlink();
		applyDetails(backlink, request, placement, resolveOwner(request.ownerId()));
		backlink.setStatus(status);
		backlink.setDates(dates);
		Backlink saved = insert(backlink, BacklinkOrigin.MANUAL, actor);
		auditService.record(AuditAction.BACKLINK_CREATED, actor.id(), ENTITY, saved.getId(), createdDetails(saved),
				client);
		return toItem(saved, calendar.today(), actor);
	}

	@Transactional
	public BacklinkItem update(Long id, SaveBacklink request, AuthenticatedUser actor, ClientInfo client) {
		Backlink backlink = load(id);
		requireVersion(backlink, request.version());
		BacklinkStatus status = request.status() == null ? backlink.getStatus() : request.status();
		BacklinkDates before = backlink.dates();
		BacklinkDates dates = datesOf(request);
		Placement placement = resolvePlacement(request.targetPageId(), request.targetUrl(), request.linkUrl(),
				request.referringDomain(), id);
		boolean closedMonth = checkStages(status, dates, placement.linkUrl(), before, actor);
		User owner = Objects.equals(id(backlink.getOwner()), request.ownerId()) ? backlink.getOwner()
				: resolveOwner(request.ownerId());
		AuditChanges changes = new AuditChanges()
			.track("targetUrl", backlink.getTargetUrl(), placement.targetUrl())
			.track("targetPageId", backlink.getTargetPage() == null ? null : backlink.getTargetPage().getId(),
					placement.page() == null ? null : placement.page().getId())
			.track("referringDomain", backlink.getReferringDomain(), placement.referringDomain())
			.track("linkUrl", backlink.getLinkUrl(), placement.linkUrl())
			.track("anchorText", backlink.getAnchorText(), trimToNull(request.anchorText()))
			.track("linkType", backlink.getLinkType(), request.linkType())
			.track("status", backlink.getStatus(), status)
			.track("ownerId", id(backlink.getOwner()), id(owner))
			.track("domainAuthority", backlink.getDomainAuthority(), request.domainAuthority());
		trackDates(changes, before, dates);
		applyDetails(backlink, request, placement, owner);
		backlink.setStatus(status);
		backlink.setDates(dates);
		backlinkRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.BACKLINK_UPDATED, actor.id(), ENTITY, id, withClosedMonth(changes, closedMonth),
					client);
		}
		return toItem(backlink, calendar.today(), actor);
	}

	/**
	 * Moves a backlink to a status ({@link BacklinkRules#moveTo}): the stages it now needs are dated {@code date}
	 * (today when omitted), stages it no longer allows are cleared. The same date and month rules apply; audited.
	 */
	@Transactional
	public BacklinkItem changeStatus(Long id, ChangeStatus request, AuthenticatedUser actor, ClientInfo client) {
		Backlink backlink = load(id);
		requireVersion(backlink, request.version());
		BacklinkStatus from = backlink.getStatus();
		BacklinkDates before = backlink.dates();
		LocalDate date = request.date() == null ? calendar.today() : request.date();
		BacklinkDates dates = BacklinkRules.moveTo(request.status(), before, date);
		if (from == request.status() && dates.equals(before)) {
			return toItem(backlink, calendar.today(), actor);
		}
		boolean closedMonth = checkStages(request.status(), dates, backlink.getLinkUrl(), before, actor);
		AuditChanges changes = new AuditChanges().track("status", from, request.status());
		trackDates(changes, before, dates);
		backlink.setStatus(request.status());
		backlink.setDates(dates);
		backlinkRepository.flush();
		auditService.record(AuditAction.BACKLINK_STATUS_CHANGED, actor.id(), ENTITY, id,
				withClosedMonth(changes, closedMonth), client);
		return toItem(backlink, calendar.today(), actor);
	}

	/** Only while every stage date's month is open: deleting a backlink takes it out of those months' counts. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		Backlink backlink = load(id);
		requireChangeable(backlink.dates(), BacklinkDates.NONE, actor);
		backlinkRepository.delete(backlink);
		Map<String, Object> details = new HashMap<>();
		details.put("code", backlink.getCode());
		details.put("referringDomain", backlink.getReferringDomain());
		details.put("status", backlink.getStatus());
		auditService.record(AuditAction.BACKLINK_DELETED, actor.id(), ENTITY, id, details, client);
	}

	// --- shared with the CSV importer -------------------------------------------------------------------------

	/**
	 * Checks a status and its dates ({@link BacklinkRules}) and that every date that changes from {@code before} lies
	 * in a month the actor may change. The CSV importer catches the ApiException to report the row, so it must not
	 * mark the import's transaction rollback-only.
	 *
	 * @return whether a closed month changed (only a Super Admin gets that far), for the audit
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public boolean checkStages(BacklinkStatus status, BacklinkDates dates, String linkUrl, BacklinkDates before,
			AuthenticatedUser actor) {
		LocalDate today = calendar.today();
		BacklinkRules.check(status, dates, linkUrl, today);
		return requireChangeable(before, dates, actor);
	}

	/**
	 * Resolves the target page or URL, the link URL and the referring domain, normalised and checked, and rejects a
	 * second backlink from the same page to the same target. Like {@link #checkStages}, leaves the caller's
	 * transaction intact on rejection.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public Placement resolvePlacement(Long targetPageId, String targetUrl, String linkUrl, String referringDomain,
			Long exceptId) {
		SeoPage page = targetPageId == null ? null
				: pageRepository.findById(targetPageId)
					.orElseThrow(() -> ApiException.badRequest("INVALID_PAGE", "The target page was not found"));
		String target = trimToNull(targetUrl);
		if (target == null && page != null) {
			target = page.getUrl();
		}
		if (target == null) {
			throw ApiException.badRequest("TARGET_REQUIRED", "Choose the page the backlink points to, or enter its URL");
		}
		if (!isUrl(target, true)) {
			throw ApiException.badRequest("INVALID_URL", "The target must be a URL (https://…) or a path starting with /");
		}
		String link = trimToNull(linkUrl);
		if (link != null && !isUrl(link, false)) {
			throw ApiException.badRequest("INVALID_URL", "The link URL must be a full URL (https://…)");
		}
		String domain;
		if (StringUtils.hasText(referringDomain)) {
			domain = BacklinkRules.domainOf(referringDomain);
			if (domain == null) {
				throw ApiException.badRequest("INVALID_DOMAIN", "Enter the referring site's domain, e.g. dzone.com");
			}
		}
		else {
			domain = BacklinkRules.domainOf(link);
			if (domain == null) {
				throw ApiException.badRequest("DOMAIN_REQUIRED", "Enter the referring domain or the link URL");
			}
		}
		if (link != null && backlinkRepository.existsDuplicate(link, target, exceptId)) {
			throw ApiException.conflict("DUPLICATE_BACKLINK", "This page already links to " + target);
		}
		return new Placement(page, target, link, domain);
	}

	/** Saves a new backlink with its code; the caller has checked it. */
	Backlink insert(Backlink backlink, BacklinkOrigin provider, AuthenticatedUser actor) {
		backlink.setCode(codeGenerator.next(CodeGenerator.BACKLINK));
		backlink.setProvider(provider);
		backlink.setCreatedBy(userRepository.getReferenceById(actor.id()));
		return backlinkRepository.save(backlink);
	}

	static Map<String, Object> createdDetails(Backlink backlink) {
		Map<String, Object> details = new HashMap<>();
		details.put("code", backlink.getCode());
		details.put("referringDomain", backlink.getReferringDomain());
		details.put("targetUrl", backlink.getTargetUrl());
		details.put("status", backlink.getStatus());
		details.put("origin", backlink.getProvider());
		backlink.dates().byField().forEach((field, date) -> {
			if (date != null) {
				details.put(field, date.toString());
			}
		});
		return details;
	}

	/** Owners must be active Digital Marketing users. */
	User resolveOwner(Long ownerId) {
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

	// --- helpers --------------------------------------------------------------------------------------------

	/**
	 * Every stage date that differs between {@code before} and {@code after} must lie (in both versions) in a month
	 * the actor may change.
	 *
	 * @return whether one of those months is closed (possible only for a Super Admin)
	 */
	private boolean requireChangeable(BacklinkDates before, BacklinkDates after, AuthenticatedUser actor) {
		LocalDate today = calendar.today();
		Map<String, LocalDate> old = before.byField();
		Map<String, LocalDate> next = after.byField();
		boolean closed = false;
		for (String field : old.keySet()) {
			LocalDate from = old.get(field);
			LocalDate to = next.get(field);
			if (Objects.equals(from, to)) {
				continue;
			}
			for (LocalDate date : new LocalDate[] { from, to }) {
				if (date == null) {
					continue;
				}
				MarketingPeriod period = MarketingPeriod.of(date);
				if (!MarketingMonths.canCorrect(period, today, actor.isSuperAdmin())) {
					throw ApiException.conflict("MONTH_LOCKED", "The " + verb(field) + " date counts in " + period.label()
							+ ", which is closed. Only the current and previous month can change.");
				}
				closed |= !MarketingMonths.isOpen(period, today);
			}
		}
		return closed;
	}

	private BacklinkItem toItem(Backlink b, LocalDate today, AuthenticatedUser viewer) {
		List<String> locked = new ArrayList<>();
		b.dates().byField().forEach((field, date) -> {
			if (date != null && !MarketingMonths.canCorrect(MarketingPeriod.of(date), today, viewer.isSuperAdmin())) {
				locked.add(field);
			}
		});
		SeoPage page = b.getTargetPage();
		return new BacklinkItem(b.getId(), b.getCode(),
				page == null ? null : new PageRef(page.getId(), page.getTitle(), page.getUrl()), b.getTargetUrl(),
				b.getReferringDomain(), b.getLinkUrl(), b.getAnchorText(), b.getLinkType(), b.getStatus(),
				b.getSubmittedDate(), b.getApprovedDate(), b.getLiveDate(), b.getRejectedDate(), b.getLostDate(),
				UserSummary.of(b.getOwner()), b.getDomainAuthority(), b.getNotes(), b.getProvider(),
				UserSummary.of(b.getCreatedBy()), locked, b.getVersion(), b.getCreatedAt(), b.getUpdatedAt());
	}

	private static MonthActivity activity(MarketingPeriod period, StageCounts c) {
		return new MonthActivity(Period.of(period), c.submitted(), c.approved(), c.live(), c.rejected(), c.lost());
	}

	/** The Backlinks target type: the first type whose actual counts live backlinks. */
	private TargetType backlinkType() {
		return typeRepository.findAllByOrderByPositionAscNameAsc()
			.stream()
			.filter(t -> t.getActualSource() == ActualSource.BACKLINKS_LIVE)
			.findFirst()
			.orElse(null);
	}

	private static void applyDetails(Backlink backlink, SaveBacklink request, Placement placement, User owner) {
		backlink.setTargetPage(placement.page());
		backlink.setTargetUrl(placement.targetUrl());
		backlink.setLinkUrl(placement.linkUrl());
		backlink.setReferringDomain(placement.referringDomain());
		backlink.setAnchorText(trimToNull(request.anchorText()));
		backlink.setLinkType(request.linkType());
		backlink.setOwner(owner);
		backlink.setDomainAuthority(request.domainAuthority());
		backlink.setNotes(trimToNull(request.notes()));
	}

	private static BacklinkDates datesOf(SaveBacklink request) {
		return new BacklinkDates(request.submittedDate(), request.approvedDate(), request.liveDate(),
				request.rejectedDate(), request.lostDate());
	}

	private static void trackDates(AuditChanges changes, BacklinkDates before, BacklinkDates after) {
		Map<String, LocalDate> next = after.byField();
		before.byField().forEach((field, from) -> changes.track(field, from, next.get(field)));
	}

	private static Map<String, Object> withClosedMonth(AuditChanges changes, boolean closedMonth) {
		Map<String, Object> details = new HashMap<>(changes.toDetails());
		if (closedMonth) {
			details.put("closedMonth", true);
		}
		return details;
	}

	private static void requireVersion(Backlink backlink, Integer version) {
		if (version == null || !Objects.equals(backlink.getVersion(), version)) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this backlink just now. Reload and try again.");
		}
	}

	private Backlink load(Long id) {
		return backlinkRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("BACKLINK_NOT_FOUND", "Backlink not found"));
	}

	/** An absolute http(s) URL, or a site path starting with "/" when {@code allowPath} is set. */
	static boolean isUrl(String value, boolean allowPath) {
		if (allowPath && value.startsWith("/") && !value.startsWith("//") && !value.contains(" ")) {
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

	private static Specification<Backlink> spec(BacklinkFilter f) {
		Specification<Backlink> spec = (root, query, cb) -> cb.conjunction();
		if (StringUtils.hasText(f.search())) {
			String pattern = "%" + f.search().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_") + "%";
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("referringDomain")), pattern, '\\'),
					cb.like(cb.lower(root.get("linkUrl")), pattern, '\\'),
					cb.like(cb.lower(root.get("targetUrl")), pattern, '\\'),
					cb.like(cb.lower(root.get("anchorText")), pattern, '\\'), cb.like(cb.lower(root.get("code")), pattern, '\\')));
		}
		if (f.statuses() != null && !f.statuses().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("status").in(f.statuses()));
		}
		if (f.types() != null && !f.types().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("linkType").in(f.types()));
		}
		if (f.ownerId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
		}
		if (f.targetPageId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("targetPage").get("id"), f.targetPageId()));
		}
		if (f.period() != null) {
			LocalDate from = f.period().firstDay();
			LocalDate to = f.period().lastDay();
			List<String> fields = f.stage() == null ? List.of("submittedDate", "approvedDate", "liveDate", "rejectedDate", "lostDate")
					: List.of(fieldOf(f.stage()));
			spec = spec.and((root, query, cb) -> cb.or(fields.stream()
				.map(field -> cb.between(root.<LocalDate>get(field), from, to))
				.toArray(jakarta.persistence.criteria.Predicate[]::new)));
		}
		return spec;
	}

	private static String fieldOf(StageKind stage) {
		return switch (stage) {
			case SUBMITTED -> "submittedDate";
			case APPROVED -> "approvedDate";
			case LIVE -> "liveDate";
			case REJECTED -> "rejectedDate";
			case LOST -> "lostDate";
		};
	}

	/** "submittedDate" → "submitted". */
	private static String verb(String field) {
		return field.substring(0, field.length() - "Date".length());
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
