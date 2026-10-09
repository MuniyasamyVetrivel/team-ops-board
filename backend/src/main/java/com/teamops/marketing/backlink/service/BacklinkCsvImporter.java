package com.teamops.marketing.backlink.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.marketing.backlink.entity.Backlink;
import com.teamops.marketing.backlink.entity.BacklinkDates;
import com.teamops.marketing.backlink.entity.BacklinkOrigin;
import com.teamops.marketing.backlink.entity.BacklinkStatus;
import com.teamops.marketing.backlink.entity.BacklinkType;
import com.teamops.marketing.backlink.service.BacklinkService.Placement;
import com.teamops.marketing.seo.entity.SeoPage;
import com.teamops.marketing.seo.repository.SeoPageRepository;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Imports backlinks, e.g. an outreach tracker. Rows follow the same rules as backlinks entered by hand: the dates
 * the status needs, in order, not in the future and in open months (any past month for a Super Admin); a link from
 * the same page to the same target is rejected. A {@code target_url} that is one of our SEO pages links that page.
 */
@Component
@RequiredArgsConstructor
public class BacklinkCsvImporter implements CsvImporter<BacklinkCsvImporter.Row> {

	public static final String TYPE = "backlinks";

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("target_url", "Our page: an SEO page URL, a path or a full URL", "/services/sap-testing"),
			CsvColumn.optional("referring_domain", "The linking site (defaults to the link URL's domain)", "dzone.com"),
			CsvColumn.optional("link_url", "The page that links to us (needed once live)", "https://dzone.com/articles/sap-test-automation"),
			CsvColumn.optional("anchor_text", "Link text", "SAP testing services"),
			CsvColumn.optional("link_type", "GUEST_POST (default), DIRECTORY, BUSINESS_LISTING, PROFILE, FORUM, SOCIAL_BOOKMARK, PRESS_RELEASE, RESOURCE_PAGE, BLOG_COMMENT or OTHER", "GUEST_POST"),
			CsvColumn.optional("status", "PROSPECTED (default), SUBMITTED, APPROVED, LIVE, REJECTED or LOST", "LIVE"),
			CsvColumn.optional("submitted_date", "YYYY-MM-DD (needed from SUBMITTED on)", "2026-10-01"),
			CsvColumn.optional("approved_date", "YYYY-MM-DD", "2026-10-03"),
			CsvColumn.optional("live_date", "YYYY-MM-DD (needed for LIVE and LOST)", "2026-10-06"),
			CsvColumn.optional("rejected_date", "YYYY-MM-DD (needed for REJECTED)", ""),
			CsvColumn.optional("lost_date", "YYYY-MM-DD (needed for LOST)", ""),
			CsvColumn.optional("domain_authority", "0–100", "58"),
			CsvColumn.optional("owner_email", "A Digital Marketing user", "karthik.raman@teamops.local"),
			CsvColumn.optional("notes", "Up to 2000 characters", ""));

	private final BacklinkService backlinkService;

	private final SeoPageRepository pageRepository;

	private final UserRepository userRepository;

	private final AuditService auditService;

	public record Row(Long targetPageId, String targetUrl, String linkUrl, String referringDomain, String anchorText,
			BacklinkType linkType, BacklinkStatus status, BacklinkDates dates, Integer domainAuthority, Long ownerId,
			String notes) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public String label() {
		return "Backlinks";
	}

	@Override
	public String description() {
		return "Backlinks from an outreach tracker. Each stage counts in the month of its date; links already recorded are rejected.";
	}

	@Override
	public String permission() {
		return BacklinkService.BACKLINK_EDIT;
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
		Map<String, Long> pages = pageRepository.findAll()
			.stream()
			.collect(Collectors.toMap(p -> p.getUrl().toLowerCase(Locale.ROOT), SeoPage::getId, (a, b) -> a));

		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String targetUrl = row.text("target_url", 500);
				String domain = row.text("referring_domain", 255);
				String linkUrl = row.url("link_url", 700, false);
				String anchor = row.text("anchor_text", 255);
				BacklinkType type = row.choice("link_type", BacklinkType.class);
				BacklinkStatus status = row.choice("status", BacklinkStatus.class);
				BacklinkDates dates = new BacklinkDates(row.date("submitted_date"), row.date("approved_date"),
						row.date("live_date"), row.date("rejected_date"), row.date("lost_date"));
				Integer authority = row.integer("domain_authority", 0, 100);
				String ownerEmail = row.email("owner_email");
				String notes = row.text("notes", 2000);
				if (!row.valid()) {
					return null;
				}
				BacklinkStatus effective = status == null ? BacklinkStatus.PROSPECTED : status;
				Long pageId = pages.get(targetUrl.toLowerCase(Locale.ROOT));

				Placement placement = null;
				try {
					placement = backlinkService.resolvePlacement(pageId, targetUrl, linkUrl, domain, null);
				}
				catch (ApiException ex) {
					row.error(placementColumn(ex), ex.getMessage());
				}
				try {
					backlinkService.checkStages(effective, dates, linkUrl, BacklinkDates.NONE, actor);
				}
				catch (ApiException ex) {
					row.error(ex.getCode().equals("LINK_URL_REQUIRED") ? "link_url" : "status", ex.getMessage());
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
				if (!row.valid() || placement == null) {
					return null;
				}
				return new Row(pageId, placement.targetUrl(), placement.linkUrl(), placement.referringDomain(), anchor,
						type == null ? BacklinkType.GUEST_POST : type, effective, dates, authority, ownerId, notes);
			}

			@Override
			public String key(Row record) {
				String target = record.targetUrl().toLowerCase(Locale.ROOT);
				return record.linkUrl() != null ? "link:" + record.linkUrl().toLowerCase(Locale.ROOT) + "|" + target
						: "domain:" + record.referringDomain() + "|" + target;
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					Placement placement = backlinkService.resolvePlacement(record.targetPageId(), record.targetUrl(),
							record.linkUrl(), record.referringDomain(), null);
					backlinkService.checkStages(record.status(), record.dates(), placement.linkUrl(), BacklinkDates.NONE,
							actor);
					Backlink backlink = new Backlink();
					backlink.setTargetPage(placement.page());
					backlink.setTargetUrl(placement.targetUrl());
					backlink.setLinkUrl(placement.linkUrl());
					backlink.setReferringDomain(placement.referringDomain());
					backlink.setAnchorText(record.anchorText());
					backlink.setLinkType(record.linkType());
					backlink.setStatus(record.status());
					backlink.setDates(record.dates());
					backlink.setDomainAuthority(record.domainAuthority());
					backlink.setOwner(record.ownerId() == null ? null : userRepository.getReferenceById(record.ownerId()));
					backlink.setNotes(record.notes());
					Backlink saved = backlinkService.insert(backlink, BacklinkOrigin.CSV, actor);
					auditService.record(AuditAction.BACKLINK_CREATED, actor.id(), BacklinkService.ENTITY, saved.getId(),
							BacklinkService.createdDetails(saved), null);
				}
				return records.size();
			}

		};
	}

	/** The column a placement problem belongs to. */
	private static String placementColumn(ApiException ex) {
		return switch (ex.getCode()) {
			case "INVALID_DOMAIN", "DOMAIN_REQUIRED" -> "referring_domain";
			case "DUPLICATE_BACKLINK" -> "link_url";
			case "INVALID_URL" -> ex.getMessage().startsWith("The link URL") ? "link_url" : "target_url";
			default -> "target_url";
		};
	}

}
