package com.teamops.marketing.lead.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
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
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.common.MarketingMath;
import com.teamops.marketing.common.MarketingMonths;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.content.entity.ContentItem;
import com.teamops.marketing.content.repository.ContentItemRepository;
import com.teamops.marketing.dto.MarketingDtos.Period;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.entity.EmailCampaignStatus;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.marketing.lead.dto.LeadDtos.ChangeStatus;
import com.teamops.marketing.lead.dto.LeadDtos.LeadItem;
import com.teamops.marketing.lead.dto.LeadDtos.LeadLink;
import com.teamops.marketing.lead.dto.LeadDtos.LeadSummary;
import com.teamops.marketing.lead.dto.LeadDtos.LeadTrend;
import com.teamops.marketing.lead.dto.LeadDtos.LinkCount;
import com.teamops.marketing.lead.dto.LeadDtos.LinkOption;
import com.teamops.marketing.lead.dto.LeadDtos.MonthCounts;
import com.teamops.marketing.lead.dto.LeadDtos.SaveLead;
import com.teamops.marketing.lead.dto.LeadDtos.SourceCount;
import com.teamops.marketing.lead.dto.LeadDtos.StatusCount;
import com.teamops.marketing.lead.entity.LeadOrigin;
import com.teamops.marketing.lead.entity.LeadStatus;
import com.teamops.marketing.lead.entity.MarketingLead;
import com.teamops.marketing.lead.repository.LeadQuery;
import com.teamops.marketing.lead.repository.LeadQuery.LinkKind;
import com.teamops.marketing.lead.repository.MarketingLeadRepository;
import com.teamops.marketing.paid.entity.AdPlatform;
import com.teamops.marketing.paid.entity.PaidCampaign;
import com.teamops.marketing.paid.entity.PaidCampaignStatus;
import com.teamops.marketing.paid.repository.PaidCampaignRepository;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.marketing.target.dto.TargetDtos.TargetItem;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.marketing.target.repository.TargetTypeRepository;
import com.teamops.marketing.target.service.TargetService;
import com.teamops.user.dto.UserSummary;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Marketing leads (brief sections 43–44): LEAD_VIEW reads, LEAD_EDIT changes. Leads are shared across the Digital
 * Marketing team. A lead counts in the month of its lead date towards the Website Leads target and its source's target,
 * so its date and source can only change (and it can only be deleted) while that month is open: the current or
 * previous business month, any past month for a Super Admin ({@link MarketingMonths}). Contact details, status, owner,
 * link and notes stay editable. A lead may name the campaign or content that brought it in ({@link LeadRules}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeadService {

	public static final String LEAD_EDIT = "LEAD_EDIT";

	public static final String TARGET_VIEW = "TARGET_VIEW";

	public static final int EXPORT_LIMIT = 10_000;

	public static final int DEFAULT_TREND_MONTHS = 12;

	public static final int MAX_TREND_MONTHS = 36;

	public static final int LINK_OPTIONS = 20;

	public static final int TOP_LINKS = 10;

	static final String ENTITY = "MARKETING_LEAD";

	private final MarketingLeadRepository leadRepository;

	private final LeadQuery leadQuery;

	private final EmailCampaignRepository emailCampaignRepository;

	private final PaidCampaignRepository paidCampaignRepository;

	private final ContentItemRepository contentItemRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final TargetService targetService;

	private final TargetTypeRepository typeRepository;

	private final CodeGenerator codeGenerator;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	/** List filters; every field is optional. {@code period} limits the list to leads dated in that month. */
	public record LeadFilter(String search, Set<LeadSource> sources, Set<LeadStatus> statuses, Long ownerId,
			MarketingPeriod period, Long emailCampaignId, Long paidCampaignId, Long contentItemId) {
	}

	/** The campaign or content a lead names, resolved and checked against its source and date. */
	record Links(EmailCampaign email, PaidCampaign paid, ContentItem content) {

		static final Links NONE = new Links(null, null, null);

	}

	public MarketingPeriod period(Integer month, Integer year) {
		return MarketingPeriod.resolve(month, year, calendar.today());
	}

	// --- reading ----------------------------------------------------------------------------------------------

	public PageResponse<LeadItem> search(LeadFilter filter, Pageable pageable, AuthenticatedUser viewer) {
		LocalDate today = calendar.today();
		return PageResponse.of(leadRepository.findAll(spec(filter), pageable).map(lead -> toItem(lead, today, viewer)));
	}

	public LeadItem get(Long id, AuthenticatedUser viewer) {
		return toItem(load(id), calendar.today(), viewer);
	}

	/**
	 * The month's leads by source against the comparison month and (for viewers with TARGET_VIEW) their targets, by
	 * status, and the campaigns and content that brought the most leads in.
	 */
	public LeadSummary summary(MarketingPeriod period, MarketingPeriod comparison, Long ownerId,
			AuthenticatedUser viewer) {
		Map<LeadSource, Long> current = leadQuery.monthly(period, period, ownerId, null).getOrDefault(period, Map.of());
		Map<LeadSource, Long> before = leadQuery.monthly(comparison, comparison, ownerId, null)
			.getOrDefault(comparison, Map.of());
		Map<LeadStatus, Long> statuses = leadQuery.byStatus(period, ownerId);
		long total = sum(current);

		boolean targetsVisible = viewer.hasPermission(TARGET_VIEW);
		TargetItem totalTarget = null;
		Map<LeadSource, TargetItem> sourceTargets = new EnumMap<>(LeadSource.class);
		if (targetsVisible) {
			Map<Long, TargetType> types = typeRepository.findAllByOrderByPositionAscNameAsc()
				.stream()
				.collect(Collectors.toMap(TargetType::getId, Function.identity()));
			for (TargetItem item : targetService.monthly(period, null, null, viewer).targets()) {
				TargetType type = types.get(item.type().id());
				if (type == null) {
					continue;
				}
				if (type.getActualSource() == ActualSource.LEADS && totalTarget == null) {
					totalTarget = item;
				}
				else if (type.getActualSource() == ActualSource.LEADS_BY_SOURCE && type.getLeadSourceFilter() != null) {
					sourceTargets.putIfAbsent(type.getLeadSourceFilter(), item);
				}
			}
		}

		List<SourceCount> bySource = new ArrayList<>();
		for (LeadSource source : LeadSource.values()) {
			bySource.add(new SourceCount(source, current.getOrDefault(source, 0L), before.getOrDefault(source, 0L),
					sourceTargets.get(source)));
		}
		List<StatusCount> byStatus = new ArrayList<>();
		for (LeadStatus status : LeadStatus.values()) {
			byStatus.add(new StatusCount(status, statuses.getOrDefault(status, 0L)));
		}
		List<LinkCount> topLinks = leadQuery.topLinks(period, ownerId, TOP_LINKS)
			.stream()
			.map(l -> new LinkCount(l.kind(), l.id(), l.name(), l.leads()))
			.toList();
		return new LeadSummary(Period.of(period), Period.of(comparison), total, sum(before),
				MarketingMath.percent(statuses.getOrDefault(LeadStatus.CONVERTED, 0L), total), targetsVisible,
				totalTarget, bySource, byStatus, topLinks);
	}

	/** Leads per month and source for {@code months} months ending with {@code end}, oldest first. */
	public LeadTrend trend(MarketingPeriod end, Integer months, Long ownerId, LeadSource source) {
		int count = months == null ? DEFAULT_TREND_MONTHS : Math.clamp(months, 1, MAX_TREND_MONTHS);
		MarketingPeriod start = end.plusMonths(-(count - 1));
		Map<MarketingPeriod, Map<LeadSource, Long>> counts = leadQuery.monthly(start, end, ownerId, source);
		List<MonthCounts> points = new ArrayList<>();
		for (MarketingPeriod p = start; !p.firstDay().isAfter(end.firstDay()); p = p.next()) {
			Map<LeadSource, Long> month = counts.getOrDefault(p, Map.of());
			Map<LeadSource, Long> bySource = new EnumMap<>(LeadSource.class);
			for (LeadSource s : LeadSource.values()) {
				bySource.put(s, month.getOrDefault(s, 0L));
			}
			points.add(new MonthCounts(Period.of(p), sum(month), bySource));
		}
		return new LeadTrend(points);
	}

	/** Campaigns or content a lead of the given kind can name, newest first, matching {@code search}. */
	public List<LinkOption> linkOptions(LinkKind kind, String search) {
		String pattern = pattern(search);
		Pageable first = PageRequest.of(0, LINK_OPTIONS);
		return switch (kind) {
			case EMAIL_CAMPAIGN -> emailCampaignRepository.findLinkable(pattern, first)
				.stream()
				.map(c -> new LinkOption(kind, c.getId(), c.getName(), c.getCampaignDate(), c.getCampaignType().name()))
				.toList();
			case PAID_CAMPAIGN -> paidCampaignRepository.findLinkable(pattern, first)
				.stream()
				.map(c -> new LinkOption(kind, c.getId(), c.getName(), c.getStartDate(), c.getPlatform().name()))
				.toList();
			case CONTENT -> contentItemRepository.findLinkable(pattern, first)
				.stream()
				.map(c -> new LinkOption(kind, c.getId(), c.getTitle(), c.getPublicationDate(), c.getContentType().name()))
				.toList();
		};
	}

	/** The lead table as CSV (same filters, newest first), at most {@link #EXPORT_LIMIT} rows. */
	public byte[] export(LeadFilter filter) {
		List<MarketingLead> leads = leadRepository
			.findAll(spec(filter), PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Order.desc("leadDate"), Sort.Order.desc("id"))))
			.getContent();
		List<List<String>> rows = leads.stream().map(l -> {
			LeadLink link = linkOf(l);
			return List.of(l.getCode(), l.getName(), text(l.getCompany()), text(l.getEmail()), text(l.getPhone()),
					l.getSource().name(), link == null ? "" : link.name(), l.getLeadDate().toString(), l.getStatus().name(),
					l.getOwner() == null ? "" : l.getOwner().getFullName(),
					l.getDepartment() == null ? "" : l.getDepartment().getName(), text(l.getNotes()));
		}).toList();
		return CsvWriter.write(List.of("Lead ID", "Name", "Company", "Email", "Phone", "Source", "Campaign / content",
				"Lead date", "Status", "Owner", "Department", "Notes"), rows);
	}

	// --- changes ----------------------------------------------------------------------------------------------

	@Transactional
	public LeadItem create(SaveLead request, AuthenticatedUser actor, ClientInfo client) {
		requireCountable(request.leadDate(), actor);
		Links links = resolveLinks(request.source(), request.emailCampaignId(), request.paidCampaignId(),
				request.contentItemId(), request.leadDate());
		MarketingLead lead = new MarketingLead();
		lead.setSource(request.source());
		lead.setLeadDate(request.leadDate());
		applyDetails(lead, request, links, resolveDepartment(request.departmentId()), resolveOwner(request.ownerId()));
		lead.setStatus(request.status() == null ? LeadStatus.NEW : request.status());
		MarketingLead saved = insert(lead, LeadOrigin.MANUAL, null, actor);
		auditService.record(AuditAction.LEAD_CREATED, actor.id(), ENTITY, saved.getId(), createdDetails(saved), client);
		return toItem(saved, calendar.today(), actor);
	}

	@Transactional
	public LeadItem update(Long id, SaveLead request, AuthenticatedUser actor, ClientInfo client) {
		MarketingLead lead = load(id);
		if (request.version() == null || !Objects.equals(lead.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this lead just now. Reload and try again.");
		}
		LocalDate today = calendar.today();
		boolean recounted = !lead.getLeadDate().equals(request.leadDate()) || lead.getSource() != request.source();
		if (recounted) {
			// Moving a lead changes the counts of both months, so both must be open.
			requireOpen(lead.getLeadDate(), actor, "This lead counts towards");
			requireCountable(request.leadDate(), actor);
		}
		Links links = resolveLinks(request.source(), request.emailCampaignId(), request.paidCampaignId(),
				request.contentItemId(), request.leadDate());
		Department department = Objects.equals(id(lead.getDepartment()), request.departmentId()) ? lead.getDepartment()
				: resolveDepartment(request.departmentId());
		User owner = Objects.equals(id(lead.getOwner()), request.ownerId()) ? lead.getOwner()
				: resolveOwner(request.ownerId());
		LeadStatus status = request.status() == null ? lead.getStatus() : request.status();
		LeadLink before = linkOf(lead);
		AuditChanges changes = new AuditChanges().track("name", lead.getName(), request.name().trim())
			.track("company", lead.getCompany(), trimToNull(request.company()))
			.track("email", lead.getEmail(), trimToNull(request.email()))
			.track("phone", lead.getPhone(), trimToNull(request.phone()))
			.track("source", lead.getSource(), request.source())
			.track("leadDate", lead.getLeadDate(), request.leadDate())
			.track("status", lead.getStatus(), status)
			.track("link", before == null ? null : before.kind() + ":" + before.id(), linkKey(links))
			.track("departmentId", id(lead.getDepartment()), id(department))
			.track("ownerId", id(lead.getOwner()), id(owner));
		// Only a Super Admin gets past requireOpen for a closed month; mark those changes in the audit.
		boolean closedMonth = recounted && !(MarketingMonths.isOpen(lead.period(), today)
				&& MarketingMonths.isOpen(MarketingPeriod.of(request.leadDate()), today));
		lead.setSource(request.source());
		lead.setLeadDate(request.leadDate());
		applyDetails(lead, request, links, department, owner);
		lead.setStatus(status);
		leadRepository.flush();
		if (!changes.isEmpty()) {
			Map<String, Object> details = new HashMap<>(changes.toDetails());
			if (closedMonth) {
				details.put("closedMonth", true);
			}
			auditService.record(AuditAction.LEAD_UPDATED, actor.id(), ENTITY, id, details, client);
		}
		return toItem(lead, today, actor);
	}

	/** A status change on its own (e.g. from the list); always allowed, audited. */
	@Transactional
	public LeadItem changeStatus(Long id, ChangeStatus request, AuthenticatedUser actor, ClientInfo client) {
		MarketingLead lead = load(id);
		if (!Objects.equals(lead.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this lead just now. Reload and try again.");
		}
		LeadStatus from = lead.getStatus();
		if (from != request.status()) {
			lead.setStatus(request.status());
			leadRepository.flush();
			auditService.record(AuditAction.LEAD_STATUS_CHANGED, actor.id(), ENTITY, id,
					Map.of("from", from, "to", request.status()), client);
		}
		return toItem(lead, calendar.today(), actor);
	}

	/** Only while the lead's month is open: deleting it changes that month's counts. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		MarketingLead lead = load(id);
		requireOpen(lead.getLeadDate(), actor, "This lead counts towards");
		leadRepository.delete(lead);
		auditService.record(AuditAction.LEAD_DELETED, actor.id(), ENTITY, id,
				Map.of("code", lead.getCode(), "source", lead.getSource(), "leadDate", lead.getLeadDate().toString()),
				client);
	}

	// --- shared with the CSV importer -------------------------------------------------------------------------

	/** A lead date must not be in the future, and its month must be open for the actor. */
	void requireCountable(LocalDate leadDate, AuthenticatedUser actor) {
		if (leadDate.isAfter(calendar.today())) {
			throw ApiException.badRequest("FUTURE_LEAD_DATE", "A lead cannot be dated in the future");
		}
		requireOpen(leadDate, actor, "Leads cannot be added to");
	}

	/**
	 * Resolves the named campaign or content and checks it: one link at most, of the kind the source takes, live,
	 * and not after the lead date.
	 */
	Links resolveLinks(LeadSource source, Long emailCampaignId, Long paidCampaignId, Long contentItemId,
			LocalDate leadDate) {
		int named = (emailCampaignId == null ? 0 : 1) + (paidCampaignId == null ? 0 : 1) + (contentItemId == null ? 0 : 1);
		if (named == 0) {
			return Links.NONE;
		}
		if (named > 1) {
			throw ApiException.badRequest("ONE_LINK", "A lead can name one campaign or content item, not several");
		}
		if (emailCampaignId != null) {
			requireAccepted(source, LinkKind.EMAIL_CAMPAIGN);
			EmailCampaign campaign = emailCampaignRepository.findById(emailCampaignId)
				.orElseThrow(() -> invalidLink("The email campaign was not found"));
			if (campaign.getStatus() != EmailCampaignStatus.SENT) {
				throw invalidLink("Only a sent email campaign can bring in leads");
			}
			requireNotBefore(leadDate, campaign.getCampaignDate(), "the email campaign was sent");
			return new Links(campaign, null, null);
		}
		if (paidCampaignId != null) {
			requireAccepted(source, LinkKind.PAID_CAMPAIGN);
			PaidCampaign campaign = paidCampaignRepository.findById(paidCampaignId)
				.orElseThrow(() -> invalidLink("The paid campaign was not found"));
			if (campaign.getStatus() == PaidCampaignStatus.DRAFT) {
				throw invalidLink("A draft campaign has not brought in leads yet");
			}
			if (source == LeadSource.LINKEDIN && campaign.getPlatform() != AdPlatform.LINKEDIN) {
				throw invalidLink("A LinkedIn lead needs a LinkedIn campaign");
			}
			requireNotBefore(leadDate, campaign.getStartDate(), "the campaign started");
			return new Links(null, campaign, null);
		}
		requireAccepted(source, LinkKind.CONTENT);
		ContentItem content = contentItemRepository.findById(contentItemId)
			.orElseThrow(() -> invalidLink("The content item was not found"));
		if (!content.getStatus().isLive() || content.getPublicationDate() == null) {
			throw invalidLink("Only published content can bring in leads");
		}
		requireNotBefore(leadDate, content.getPublicationDate(), "the content was published");
		return new Links(null, null, content);
	}

	/** Saves a new lead with its code; the caller has checked it. */
	MarketingLead insert(MarketingLead lead, LeadOrigin provider, String externalId, AuthenticatedUser actor) {
		lead.setCode(codeGenerator.next(CodeGenerator.LEAD));
		lead.setProvider(provider);
		lead.setExternalId(externalId);
		lead.setCreatedBy(userRepository.getReferenceById(actor.id()));
		return leadRepository.save(lead);
	}

	static Map<String, Object> createdDetails(MarketingLead lead) {
		Map<String, Object> details = new HashMap<>();
		details.put("code", lead.getCode());
		details.put("source", lead.getSource());
		details.put("leadDate", lead.getLeadDate().toString());
		details.put("origin", lead.getProvider());
		LeadLink link = linkOf(lead);
		if (link != null) {
			details.put("link", link.kind() + ":" + link.id());
		}
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

	private LeadItem toItem(MarketingLead l, LocalDate today, AuthenticatedUser viewer) {
		boolean countLocked = !MarketingMonths.canCorrect(l.period(), today, viewer.isSuperAdmin());
		return new LeadItem(l.getId(), l.getCode(), l.getName(), l.getCompany(), l.getEmail(), l.getPhone(),
				l.getSource(), linkOf(l), l.getDepartment() == null ? null : DepartmentSummary.of(l.getDepartment()),
				l.getLeadDate(), l.getStatus(), UserSummary.of(l.getOwner()), l.getNotes(), l.getProvider(),
				l.getExternalId(), UserSummary.of(l.getCreatedBy()), countLocked, l.getVersion(), l.getCreatedAt(),
				l.getUpdatedAt());
	}

	static LeadLink linkOf(MarketingLead l) {
		if (l.getEmailCampaign() != null) {
			EmailCampaign c = l.getEmailCampaign();
			return new LeadLink(LinkKind.EMAIL_CAMPAIGN, c.getId(), c.getName(), c.getCampaignDate());
		}
		if (l.getPaidCampaign() != null) {
			PaidCampaign c = l.getPaidCampaign();
			return new LeadLink(LinkKind.PAID_CAMPAIGN, c.getId(), c.getName(), c.getStartDate());
		}
		if (l.getContentItem() != null) {
			ContentItem c = l.getContentItem();
			return new LeadLink(LinkKind.CONTENT, c.getId(), c.getTitle(), c.getPublicationDate());
		}
		return null;
	}

	private static void applyDetails(MarketingLead lead, SaveLead request, Links links, Department department,
			User owner) {
		lead.setName(request.name().trim());
		lead.setCompany(trimToNull(request.company()));
		lead.setEmail(trimToNull(request.email()));
		lead.setPhone(trimToNull(request.phone()));
		lead.setEmailCampaign(links.email());
		lead.setPaidCampaign(links.paid());
		lead.setContentItem(links.content());
		lead.setDepartment(department);
		lead.setOwner(owner);
		lead.setNotes(trimToNull(request.notes()));
	}

	private void requireOpen(LocalDate leadDate, AuthenticatedUser actor, String what) {
		MarketingPeriod period = MarketingPeriod.of(leadDate);
		if (!MarketingMonths.canCorrect(period, calendar.today(), actor.isSuperAdmin())) {
			throw ApiException.conflict("MONTH_LOCKED", what + " " + period.label()
					+ ", which is closed. Only the current and previous month can change.");
		}
	}

	private static void requireAccepted(LeadSource source, LinkKind kind) {
		if (!LeadRules.accepts(source, kind)) {
			throw ApiException.badRequest("LINK_NOT_ALLOWED", switch (kind) {
				case EMAIL_CAMPAIGN -> "Only email leads can name an email campaign";
				case PAID_CAMPAIGN -> "Only LinkedIn and paid campaign leads can name a paid campaign";
				case CONTENT -> "Only blog leads can name a content item";
			});
		}
	}

	private static void requireNotBefore(LocalDate leadDate, LocalDate linkDate, String event) {
		if (LeadRules.before(leadDate, linkDate)) {
			throw ApiException.badRequest("LEAD_BEFORE_LINK", "The lead date is before " + event + " (" + linkDate + ")");
		}
	}

	private static ApiException invalidLink(String message) {
		return ApiException.badRequest("INVALID_LINK", message);
	}

	private Department resolveDepartment(Long departmentId) {
		return departmentId == null ? null
				: departmentRepository.findById(departmentId)
					.orElseThrow(() -> ApiException.badRequest("INVALID_DEPARTMENT", "The department was not found"));
	}

	private MarketingLead load(Long id) {
		return leadRepository.findDetailedById(id)
			.orElseThrow(() -> ApiException.notFound("LEAD_NOT_FOUND", "Lead not found"));
	}

	private static Specification<MarketingLead> spec(LeadFilter f) {
		Specification<MarketingLead> spec = (root, query, cb) -> cb.conjunction();
		if (StringUtils.hasText(f.search())) {
			String pattern = "%" + f.search().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_") + "%";
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
					cb.like(cb.lower(root.get("company")), pattern, '\\'), cb.like(cb.lower(root.get("email")), pattern, '\\'),
					cb.like(cb.lower(root.get("code")), pattern, '\\')));
		}
		if (f.sources() != null && !f.sources().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("source").in(f.sources()));
		}
		if (f.statuses() != null && !f.statuses().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("status").in(f.statuses()));
		}
		if (f.ownerId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
		}
		if (f.period() != null) {
			MarketingPeriod p = f.period();
			spec = spec.and((root, query, cb) -> cb.between(root.get("leadDate"), p.firstDay(), p.lastDay()));
		}
		if (f.emailCampaignId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("emailCampaign").get("id"), f.emailCampaignId()));
		}
		if (f.paidCampaignId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("paidCampaign").get("id"), f.paidCampaignId()));
		}
		if (f.contentItemId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("contentItem").get("id"), f.contentItemId()));
		}
		return spec;
	}

	/** A LIKE pattern for the link pickers ({@code !} escapes), or null for no search. */
	private static String pattern(String search) {
		return StringUtils.hasText(search) ? "%" + search.trim().toLowerCase(Locale.ROOT).replace("!", "!!")
			.replace("%", "!%")
			.replace("_", "!_") + "%" : null;
	}

	private static String linkKey(Links links) {
		if (links.email() != null) {
			return LinkKind.EMAIL_CAMPAIGN + ":" + links.email().getId();
		}
		if (links.paid() != null) {
			return LinkKind.PAID_CAMPAIGN + ":" + links.paid().getId();
		}
		return links.content() == null ? null : LinkKind.CONTENT + ":" + links.content().getId();
	}

	private static long sum(Map<LeadSource, Long> counts) {
		return counts.values().stream().mapToLong(Long::longValue).sum();
	}

	static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	private static Long id(User user) {
		return user == null ? null : user.getId();
	}

	private static Long id(Department department) {
		return department == null ? null : department.getId();
	}

}
