package com.teamops.celebration.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;

import com.teamops.celebration.dto.CelebrationDtos.Attachment;
import com.teamops.celebration.dto.CelebrationDtos.ImportantDayDto;
import com.teamops.celebration.dto.CelebrationDtos.SaveImportantDay;
import com.teamops.celebration.entity.DayRecurrence;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.ImportantDayCategory;
import com.teamops.celebration.entity.LinkedInPost;
import com.teamops.celebration.entity.OccasionKind;
import com.teamops.celebration.entity.Region;
import com.teamops.celebration.repository.ImportantDayRepository;
import com.teamops.celebration.service.OccasionAttachmentService.Download;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.csv.CsvWriter;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Marketing important days (festivals, national and industry days, company milestones). Everyone signed in sees them;
 * CELEBRATIONS_EDIT holders change them. Changes, including creatives added or removed, are audited.
 */
@Service
@RequiredArgsConstructor
public class ImportantDayService {

	public static final String ENTITY = "IMPORTANT_DAY";

	public static final int EXPORT_LIMIT = 10_000;

	/** The export's columns, the same as the import's. */
	public static final List<String> CSV_COLUMNS = List.of("name", "date", "category", "region", "repeat",
			"description", "marketing_notes");

	private final ImportantDayRepository repository;

	private final OccasionAttachmentService attachments;

	private final LinkedInPostService linkedInPosts;

	private final UserRepository userRepository;

	private final AuditService auditService;

	private final BusinessCalendar calendar;

	private final RecycleBinService recycleBin;

	/** @param year only one-off days dated in that year, and yearly days that had started by then */
	public record DayFilter(String search, Set<ImportantDayCategory> categories, Set<Region> regions,
			DayRecurrence recurrence, Integer year) {
	}

	@Transactional(readOnly = true)
	public PageResponse<ImportantDayDto> search(DayFilter filter, Pageable pageable, AuthenticatedUser viewer) {
		Page<ImportantDay> page = repository.findAll(spec(filter), pageable);
		Map<Long, ImportantDayDto> dtos = toDtos(page.getContent(), viewer);
		return PageResponse.of(page.map(day -> dtos.get(day.getId())));
	}

	@Transactional(readOnly = true)
	public ImportantDayDto get(Long id, AuthenticatedUser viewer) {
		return toDtos(List.of(load(id)), viewer).get(id);
	}

	@Transactional(readOnly = true)
	public byte[] export(DayFilter filter) {
		List<List<String>> rows = repository
			.findAll(spec(filter), PageRequest.of(0, EXPORT_LIMIT, Sort.by("date", "name", "id")))
			.stream()
			.map(d -> List.of(d.getName(), d.getDate().toString(), d.getCategory().name(), d.getRegion().name(),
					d.getRecurrence().name(), text(d.getDescription()), text(d.getMarketingNotes())))
			.toList();
		return CsvWriter.write(CSV_COLUMNS, rows);
	}

	@Transactional
	public ImportantDayDto create(SaveImportantDay request, AuthenticatedUser actor, ClientInfo client) {
		ImportantDay day = new ImportantDay();
		day.setCreatedBy(userRepository.getReferenceById(actor.id()));
		apply(day, request);
		ImportantDay saved = repository.saveAndFlush(day);
		Map<String, Object> details = new HashMap<>();
		details.put("name", saved.getName());
		details.put("date", saved.getDate());
		details.put("region", saved.getRegion());
		details.put("recurrence", saved.getRecurrence());
		auditService.record(AuditAction.IMPORTANT_DAY_CREATED, actor.id(), ENTITY, saved.getId(), details, client);
		return get(saved.getId(), actor);
	}

	@Transactional
	public ImportantDayDto update(Long id, SaveImportantDay request, AuthenticatedUser actor, ClientInfo client) {
		ImportantDay day = load(id);
		if (request.version() == null || !Objects.equals(day.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this important day just now. Reload and try again.");
		}
		LocalDate dateBefore = day.getDate();
		DayRecurrence recurrenceBefore = day.getRecurrence();
		AuditChanges changes = new AuditChanges().track("name", day.getName(), request.name().trim())
			.track("date", day.getDate(), request.date())
			.track("category", day.getCategory(), request.category())
			.track("region", day.getRegion(), request.region())
			.track("recurrence", day.getRecurrence(), request.recurrence())
			.track("description", day.getDescription(), CelebrationText.trimToNull(request.description()))
			.track("marketingNotes", day.getMarketingNotes(), CelebrationText.trimToNull(request.marketingNotes()));
		apply(day, request);
		repository.flush();
		if (!dateBefore.equals(day.getDate()) || recurrenceBefore != day.getRecurrence()) {
			LocalDate date = day.getDate();
			boolean yearly = day.isYearly();
			linkedInPosts.relocate(OccasionKind.IMPORTANT_DAY, id, calendar.today(), occurrence -> yearly
					? (occurrence.getYear() < date.getYear() ? null : AnnualDates.in(date, occurrence.getYear())) : date);
		}
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.IMPORTANT_DAY_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		return get(id, actor);
	}

	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		ImportantDay day = load(id);
		// Creatives and LinkedIn posts stay with the record in the recycle bin (OccasionRecycleHandlers).
		recycleBin.moveToBin(day, actor);
		Map<String, Object> details = new HashMap<>();
		details.put("name", day.getName());
		details.put("date", day.getDate());
		details.put("region", day.getRegion());
		auditService.record(AuditAction.IMPORTANT_DAY_DELETED, actor.id(), ENTITY, id, details, client);
	}

	// --- creatives --------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<Attachment> attachments(Long id) {
		load(id);
		return attachments.list(OccasionKind.IMPORTANT_DAY, id);
	}

	@Transactional
	public List<Attachment> addAttachment(Long id, MultipartFile upload, AuthenticatedUser actor, ClientInfo client) {
		load(id);
		String name = attachments.add(OccasionKind.IMPORTANT_DAY, id, upload, actor.id());
		auditService.record(AuditAction.IMPORTANT_DAY_UPDATED, actor.id(), ENTITY, id, Map.of("attachmentAdded", name),
				client);
		return attachments.list(OccasionKind.IMPORTANT_DAY, id);
	}

	@Transactional(readOnly = true)
	public Download download(Long id, Long fileId) {
		return attachments.download(OccasionKind.IMPORTANT_DAY, id, fileId);
	}

	@Transactional
	public List<Attachment> deleteAttachment(Long id, Long fileId, AuthenticatedUser actor, ClientInfo client) {
		String name = attachments.delete(OccasionKind.IMPORTANT_DAY, id, fileId);
		auditService.record(AuditAction.IMPORTANT_DAY_UPDATED, actor.id(), ENTITY, id,
				Map.of("attachmentRemoved", name), client);
		return attachments.list(OccasionKind.IMPORTANT_DAY, id);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	/** The next date on or after today for a yearly day; the date itself for a one-off day. */
	static LocalDate nextOccurrence(ImportantDay day, LocalDate today) {
		return day.isYearly() ? AnnualDates.next(day.getDate(), day.getDate().getYear(), today) : day.getDate();
	}

	private void apply(ImportantDay day, SaveImportantDay request) {
		String name = request.name().trim();
		boolean taken = day.getId() == null
				? repository.existsByNameAndDateAndRegion(name, request.date(), request.region())
				: repository.existsByNameAndDateAndRegionAndIdNot(name, request.date(), request.region(), day.getId());
		if (taken) {
			throw ApiException.conflict("IMPORTANT_DAY_EXISTS",
					name + " is already on " + request.date() + " for " + request.region().label());
		}
		day.setName(name);
		day.setDate(request.date());
		day.setCategory(request.category());
		day.setRegion(request.region());
		day.setRecurrence(request.recurrence());
		day.setDescription(CelebrationText.trimToNull(request.description()));
		day.setMarketingNotes(CelebrationText.trimToNull(request.marketingNotes()));
	}

	/** DTOs for a page of days: attachment counts and the next occurrence's post in one query each. */
	private Map<Long, ImportantDayDto> toDtos(List<ImportantDay> days, AuthenticatedUser viewer) {
		LocalDate today = calendar.today();
		List<Long> ids = days.stream().map(ImportantDay::getId).toList();
		Map<Long, Long> counts = attachments.counts(OccasionKind.IMPORTANT_DAY, ids);
		Map<String, LinkedInPost> posts = linkedInPosts.forSubjects(OccasionKind.IMPORTANT_DAY, ids);
		boolean canEdit = CelebrationAccess.canEdit(viewer);
		Map<Long, ImportantDayDto> dtos = new HashMap<>();
		for (ImportantDay d : days) {
			LocalDate occurrence = nextOccurrence(d, today);
			dtos.put(d.getId(), new ImportantDayDto(d.getId(), d.getName(), d.getDate(), d.getCategory(), d.getRegion(),
					d.getRecurrence(), d.getDescription(), d.getMarketingNotes(), occurrence,
					counts.getOrDefault(d.getId(), 0L),
					LinkedInPostService.dto(OccasionKind.IMPORTANT_DAY,
							posts.get(Occasion.key(OccasionKind.IMPORTANT_DAY, d.getId(), occurrence))),
					d.getVersion(), canEdit));
		}
		return dtos;
	}

	private ImportantDay load(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> ApiException.notFound("IMPORTANT_DAY_NOT_FOUND", "Important day not found"));
	}

	private static Specification<ImportantDay> spec(DayFilter f) {
		Specification<ImportantDay> spec = (root, query, cb) -> cb.conjunction();
		if (f.search() != null && !f.search().isBlank()) {
			String pattern = CelebrationText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
					cb.like(cb.lower(root.get("description")), pattern, '\\')));
		}
		if (f.categories() != null && !f.categories().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("category").in(f.categories()));
		}
		if (f.regions() != null && !f.regions().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("region").in(f.regions()));
		}
		if (f.recurrence() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("recurrence"), f.recurrence()));
		}
		if (f.year() != null) {
			LocalDate first = LocalDate.of(f.year(), 1, 1);
			LocalDate last = LocalDate.of(f.year(), 12, 31);
			spec = spec.and((root, query, cb) -> cb.or(
					cb.and(cb.equal(root.get("recurrence"), DayRecurrence.ONE_OFF), cb.between(root.get("date"), first, last)),
					cb.and(cb.equal(root.get("recurrence"), DayRecurrence.YEARLY), cb.lessThanOrEqualTo(root.get("date"), last))));
		}
		return spec;
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

}
