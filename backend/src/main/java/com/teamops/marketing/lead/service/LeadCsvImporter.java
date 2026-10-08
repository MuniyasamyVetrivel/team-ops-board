package com.teamops.marketing.lead.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditService;
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.content.entity.ContentItem;
import com.teamops.marketing.content.repository.ContentItemRepository;
import com.teamops.marketing.email.entity.EmailCampaign;
import com.teamops.marketing.email.repository.EmailCampaignRepository;
import com.teamops.marketing.lead.entity.LeadOrigin;
import com.teamops.marketing.lead.entity.LeadStatus;
import com.teamops.marketing.lead.entity.MarketingLead;
import com.teamops.marketing.lead.repository.LeadQuery.LinkKind;
import com.teamops.marketing.lead.repository.MarketingLeadRepository;
import com.teamops.marketing.paid.entity.PaidCampaign;
import com.teamops.marketing.paid.repository.PaidCampaignRepository;
import com.teamops.marketing.service.MarketingContextService;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Imports marketing leads, e.g. a website form or CRM export. Rows follow the same rules as leads entered by hand:
 * not in the future, in an open month (any past month for a Super Admin), and a {@code campaign} that fits the source
 * (an email campaign for EMAIL, a paid campaign for LINKEDIN/PAID_CAMPAIGN, published content for BLOG), matched by
 * external id or URL, or else by a unique name. A row with an {@code external_id} already imported, or an email
 * already recorded on the same date, is rejected.
 */
@Component
@RequiredArgsConstructor
public class LeadCsvImporter implements CsvImporter<LeadCsvImporter.Row> {

	public static final String TYPE = "marketing-leads";

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("name", "Lead name", "Anita Rao"),
			CsvColumn.required("source", "ORGANIC, EMAIL, LINKEDIN, PAID_CAMPAIGN, BLOG, WEBSITE, REFERRAL or OTHER", "EMAIL"),
			CsvColumn.required("lead_date", "YYYY-MM-DD", "2026-10-06"),
			CsvColumn.optional("company", "Company", "Acme Manufacturing"),
			CsvColumn.optional("email", "Email address", "anita.rao@acme.example"),
			CsvColumn.optional("phone", "Phone", "+91 98450 00000"),
			CsvColumn.optional("status", "NEW (default), CONTACTED, QUALIFIED, CONVERTED or LOST", "NEW"),
			CsvColumn.optional("campaign", "Email or paid campaign (external id or name), or content (URL or title)", "SAP Testing Services Outreach"),
			CsvColumn.optional("owner_email", "A Digital Marketing user", "priya.menon@teamops.local"),
			CsvColumn.optional("department_code", "Department code", "DM"),
			CsvColumn.optional("notes", "Up to 2000 characters", ""),
			CsvColumn.optional("external_id", "The lead's id in the source system (prevents duplicates)", "WEB-20431"));

	private final LeadService leadService;

	private final MarketingLeadRepository leadRepository;

	private final EmailCampaignRepository emailCampaignRepository;

	private final PaidCampaignRepository paidCampaignRepository;

	private final ContentItemRepository contentItemRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final AuditService auditService;

	public record Row(String name, LeadSource source, LocalDate leadDate, String company, String email, String phone,
			LeadStatus status, Long emailCampaignId, Long paidCampaignId, Long contentItemId, Long ownerId,
			Long departmentId, String notes, String externalId) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public String label() {
		return "Marketing leads";
	}

	@Override
	public String description() {
		return "Leads from a website form or CRM export. Each row counts in the month of its lead date; rows already imported are rejected.";
	}

	@Override
	public String permission() {
		return LeadService.LEAD_EDIT;
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
		Map<String, Department> departments = departmentRepository.findAll()
			.stream()
			.collect(Collectors.toMap(d -> d.getCode().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
		Matcher emails = new Matcher();
		emailCampaignRepository.findLinkable(null, Pageable.unpaged())
			.forEach((EmailCampaign c) -> emails.add(c.getId(), c.getExternalId(), c.getName()));
		Matcher paid = new Matcher();
		paidCampaignRepository.findLinkable(null, Pageable.unpaged())
			.forEach((PaidCampaign c) -> paid.add(c.getId(), c.getExternalId(), c.getName()));
		Matcher content = new Matcher();
		contentItemRepository.findLinkable(null, Pageable.unpaged())
			.forEach((ContentItem c) -> content.add(c.getId(), c.getUrl(), c.getTitle()));

		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String name = row.text("name", 200);
				LeadSource source = row.choice("source", LeadSource.class);
				LocalDate leadDate = row.date("lead_date");
				String company = row.text("company", 200);
				String email = row.email("email");
				String phone = row.text("phone", 50);
				LeadStatus status = row.choice("status", LeadStatus.class);
				String campaign = row.text("campaign", 1000);
				String ownerEmail = row.email("owner_email");
				String departmentCode = row.text("department_code", 50);
				String notes = row.text("notes", 2000);
				String externalId = row.text("external_id", 100);
				if (!row.valid()) {
					return null;
				}
				try {
					leadService.requireCountable(leadDate, actor);
				}
				catch (ApiException ex) {
					row.error("lead_date", ex.getMessage());
				}

				Long emailCampaignId = null;
				Long paidCampaignId = null;
				Long contentItemId = null;
				if (campaign != null) {
					LinkKind kind = LeadRules.linkFor(source).orElse(null);
					if (kind == null) {
						row.error("campaign", "A " + source.name().toLowerCase(Locale.ROOT).replace('_', ' ')
								+ " lead has no campaign or content");
					}
					else {
						Matcher matcher = switch (kind) {
							case EMAIL_CAMPAIGN -> emails;
							case PAID_CAMPAIGN -> paid;
							case CONTENT -> content;
						};
						String problem = matcher.problem(campaign);
						if (problem != null) {
							row.error("campaign", problem);
						}
						else {
							Long id = matcher.find(campaign);
							switch (kind) {
								case EMAIL_CAMPAIGN -> emailCampaignId = id;
								case PAID_CAMPAIGN -> paidCampaignId = id;
								case CONTENT -> contentItemId = id;
							}
							try {
								leadService.resolveLinks(source, emailCampaignId, paidCampaignId, contentItemId, leadDate);
							}
							catch (ApiException ex) {
								row.error("campaign", ex.getMessage());
							}
						}
					}
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
				Long departmentId = null;
				if (departmentCode != null) {
					Department department = departments.get(departmentCode.toLowerCase(Locale.ROOT));
					if (department == null) {
						row.error("department_code", "No department with this code");
					}
					else {
						departmentId = department.getId();
					}
				}
				if (externalId != null && leadRepository.existsByProviderAndExternalId(LeadOrigin.CSV, externalId)) {
					row.error("external_id", "This lead was already imported");
				}
				else if (email != null && leadRepository.existsByEmailIgnoreCaseAndLeadDate(email, leadDate)) {
					row.error("email", "A lead with this email is already recorded on this date");
				}
				return row.valid() ? new Row(name, source, leadDate, company, email, phone,
						status == null ? LeadStatus.NEW : status, emailCampaignId, paidCampaignId, contentItemId, ownerId,
						departmentId, notes, externalId) : null;
			}

			@Override
			public String key(Row record) {
				if (record.externalId() != null) {
					return "id:" + record.externalId();
				}
				return record.email() != null ? "email:" + record.email().toLowerCase(Locale.ROOT) + "|" + record.leadDate()
						: "name:" + record.name().toLowerCase(Locale.ROOT) + "|" + record.leadDate();
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					LeadService.Links links = leadService.resolveLinks(record.source(), record.emailCampaignId(),
							record.paidCampaignId(), record.contentItemId(), record.leadDate());
					MarketingLead lead = new MarketingLead();
					lead.setName(record.name());
					lead.setSource(record.source());
					lead.setLeadDate(record.leadDate());
					lead.setCompany(record.company());
					lead.setEmail(record.email());
					lead.setPhone(record.phone());
					lead.setStatus(record.status());
					lead.setEmailCampaign(links.email());
					lead.setPaidCampaign(links.paid());
					lead.setContentItem(links.content());
					lead.setOwner(record.ownerId() == null ? null : userRepository.getReferenceById(record.ownerId()));
					lead.setDepartment(record.departmentId() == null ? null
							: departmentRepository.getReferenceById(record.departmentId()));
					lead.setNotes(record.notes());
					MarketingLead saved = leadService.insert(lead, LeadOrigin.CSV, record.externalId(), actor);
					auditService.record(AuditAction.LEAD_CREATED, actor.id(), LeadService.ENTITY, saved.getId(),
							LeadService.createdDetails(saved), null);
				}
				return records.size();
			}

		};
	}

	/** Finds a campaign or content item by external id / URL, or else by a name that only one of them has. */
	private static final class Matcher {

		private final Map<String, Long> byKey = new HashMap<>();

		private final Map<String, Long> byName = new HashMap<>();

		private final Map<String, Integer> nameCounts = new HashMap<>();

		void add(Long id, String key, String name) {
			if (key != null) {
				byKey.putIfAbsent(key.toLowerCase(Locale.ROOT), id);
			}
			String n = name.toLowerCase(Locale.ROOT);
			byName.putIfAbsent(n, id);
			nameCounts.merge(n, 1, Integer::sum);
		}

		/** Why {@code reference} matches nothing usable, or null when it matches exactly one. */
		String problem(String reference) {
			String r = reference.toLowerCase(Locale.ROOT);
			if (byKey.containsKey(r)) {
				return null;
			}
			if (nameCounts.getOrDefault(r, 0) > 1) {
				return "More than one match for this name; use the external id or URL";
			}
			return byName.containsKey(r) ? null : "No live campaign or published content matches this";
		}

		Long find(String reference) {
			String r = reference.toLowerCase(Locale.ROOT);
			Long id = byKey.get(r);
			return id != null ? id : byName.get(r);
		}

	}

}
