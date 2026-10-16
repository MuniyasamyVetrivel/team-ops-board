package com.teamops.vendor.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.teamops.vendor.dto.VendorDtos.ContactDto;
import com.teamops.vendor.dto.VendorDtos.SaveContact;
import com.teamops.vendor.dto.VendorDtos.SaveVendor;
import com.teamops.vendor.dto.VendorDtos.VendorDto;
import com.teamops.vendor.dto.VendorDtos.VendorRef;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorCategory;
import com.teamops.vendor.entity.VendorContact;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.repository.VendorContractRepository;
import com.teamops.vendor.repository.VendorContractRepository.VendorSummary;
import com.teamops.vendor.repository.VendorRepository;
import com.teamops.vendor.service.VendorLinkSource.LinkGroup;
import com.teamops.vendor.service.VendorLinkSource.Links;

import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Vendors and their contacts (Phase X7). The name is unique, including vendors in the recycle bin. A vendor with live
 * contracts, or used by records of other modules ({@link VendorLinkSource}: licences and expenses from Phase X8), is
 * not deleted. Changes are audited.
 */
@Service
@RequiredArgsConstructor
public class VendorService {

	public static final String ENTITY = "VENDOR";

	public static final int EXPORT_LIMIT = 10_000;

	/** How many linked records of each kind the vendor page shows. */
	public static final int LINK_LIMIT = 10;

	/** The export's columns: the same as the import's, so an export can be edited and imported elsewhere. */
	public static final List<String> CSV_COLUMNS = List.of("name", "tax_id", "category", "country", "address", "website",
			"payment_terms", "status", "rating", "notes", "contact_name", "contact_role", "contact_email",
			"contact_phone");

	private final VendorRepository repository;

	private final VendorContractRepository contracts;

	private final List<VendorLinkSource> linkSources;

	private final AuditService auditService;

	private final RecycleBinService recycleBin;

	private final BusinessCalendar calendar;

	public record VendorFilter(String search, VendorCategory category, VendorStatus status, String country) {
	}

	@Transactional(readOnly = true)
	public PageResponse<VendorDto> search(VendorFilter filter, Pageable pageable) {
		Page<Vendor> page = repository.findAll(spec(filter), pageable);
		Map<Long, VendorSummary> summaries = summaries(page.map(Vendor::getId).getContent());
		return PageResponse.of(page.map(vendor -> toDto(vendor, summaries.get(vendor.getId()))));
	}

	@Transactional(readOnly = true)
	public VendorDto get(Long id) {
		Vendor vendor = load(id);
		return toDto(vendor, summaries(List.of(id)).get(id));
	}

	/**
	 * A live vendor for another record to point at (contracts; licences and expenses in Phase X8); 400 UNKNOWN_VENDOR
	 * when there is none with this id.
	 */
	@Transactional(readOnly = true)
	public VendorRef reference(Long id) {
		return VendorRef.of(repository.findById(id)
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_VENDOR", "Vendor not found")));
	}

	/** Records of other modules linked to the vendor, by kind, as far as the viewer may see them. */
	@Transactional(readOnly = true)
	public List<LinkGroup> links(Long id, AuthenticatedUser viewer) {
		load(id);
		List<LinkGroup> groups = new ArrayList<>();
		for (VendorLinkSource source : linkSources) {
			if (source.visibleTo(viewer)) {
				Links links = source.linksFor(id, LINK_LIMIT, viewer);
				groups.add(new LinkGroup(source.kind(), source.label(), links.total(), links.items()));
			}
		}
		return groups;
	}

	@Transactional
	public VendorDto create(SaveVendor request, AuthenticatedUser actor, ClientInfo info) {
		Vendor vendor = new Vendor();
		apply(vendor, request);
		Vendor saved = repository.saveAndFlush(vendor);
		auditService.record(AuditAction.VENDOR_CREATED, actor.id(), ENTITY, saved.getId(),
				Map.of("name", saved.getName(), "category", saved.getCategory()), info);
		return toDto(saved, null);
	}

	@Transactional
	public VendorDto update(Long id, SaveVendor request, AuthenticatedUser actor, ClientInfo info) {
		Vendor vendor = load(id);
		if (request.version() == null || !Objects.equals(vendor.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this vendor just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(vendor);
		apply(vendor, request);
		AuditChanges changes = new AuditChanges();
		snapshot(vendor).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.VENDOR_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), info);
		}
		return get(id);
	}

	/**
	 * Moves the vendor to the recycle bin; its contacts stay with it. 409 VENDOR_HAS_CONTRACTS while it has live
	 * contracts, 409 VENDOR_IN_USE while live records of another module use it. Audited.
	 */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo info) {
		Vendor vendor = load(id);
		if (contracts.existsByVendorId(id)) {
			throw ApiException.conflict("VENDOR_HAS_CONTRACTS",
					vendor.getName() + " still has contracts. Delete them first.");
		}
		for (VendorLinkSource source : linkSources) {
			long using = source.countUsing(id);
			if (using > 0) {
				throw ApiException.conflict("VENDOR_IN_USE", vendor.getName() + " is still used by " + using + " "
						+ source.label().toLowerCase(Locale.ROOT) + ". Change or delete those first.");
			}
		}
		recycleBin.moveToBin(vendor, actor);
		auditService.record(AuditAction.VENDOR_DELETED, actor.id(), ENTITY, id, Map.of("name", vendor.getName()), info);
	}

	/** CSV in the import's columns, with the main contact. */
	@Transactional(readOnly = true)
	public byte[] export(VendorFilter filter) {
		List<List<String>> rows = repository.findAll(spec(filter), Sort.by("name", "id"))
			.stream()
			.limit(EXPORT_LIMIT)
			.map(v -> {
				VendorContact main = v.getContacts().isEmpty() ? null : v.getContacts().getFirst();
				return List.of(v.getName(), text(v.getTaxId()), v.getCategory().name(), text(v.getCountry()),
						text(v.getAddress()), text(v.getWebsite()), text(v.getPaymentTerms()), v.getStatus().name(),
						v.getRating() == null ? "" : v.getRating().toString(), text(v.getNotes()),
						main == null ? "" : main.getName(), main == null ? "" : text(main.getRole()),
						main == null ? "" : text(main.getEmail()), main == null ? "" : text(main.getPhone()));
			})
			.toList();
		return CsvWriter.write(CSV_COLUMNS, rows);
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private void apply(Vendor vendor, SaveVendor request) {
		String name = request.name().trim();
		boolean taken = vendor.getId() == null ? repository.existsByName(name)
				: repository.existsByNameAndIdNot(name, vendor.getId());
		if (taken) {
			throw ApiException.conflict("VENDOR_NAME_TAKEN", "Another vendor is already called " + name);
		}
		if (!name.equalsIgnoreCase(vendor.getName() == null ? "" : vendor.getName())) {
			recycleBin.requireNotInBin(Vendor.class, Map.of("name", name), "The vendor name " + name);
		}
		vendor.setName(name);
		vendor.setTaxId(VendorText.trimToNull(request.taxId()));
		vendor.setCategory(request.category() == null ? VendorCategory.OTHER : request.category());
		vendor.setCountry(VendorText.trimToNull(request.country()));
		vendor.setAddress(VendorText.trimToNull(request.address()));
		vendor.setWebsite(VendorText.website(request.website()));
		vendor.setPaymentTerms(VendorText.trimToNull(request.paymentTerms()));
		vendor.setStatus(request.status() == null ? VendorStatus.ACTIVE : request.status());
		vendor.setRating(request.rating());
		vendor.setNotes(VendorText.trimToNull(request.notes()));
		replaceContacts(vendor, request.contacts() == null ? List.of() : request.contacts());
	}

	/** Updates the contacts named by id, removes the ones left out and adds those without an id, in order. */
	static void replaceContacts(Vendor vendor, List<SaveContact> requested) {
		Map<Long, VendorContact> existing = new HashMap<>();
		vendor.getContacts().forEach(c -> existing.put(c.getId(), c));
		List<VendorContact> next = new ArrayList<>();
		for (int i = 0; i < requested.size(); i++) {
			SaveContact request = requested.get(i);
			VendorContact contact;
			if (request.id() == null) {
				contact = new VendorContact();
				contact.setVendor(vendor);
			}
			else {
				contact = existing.get(request.id());
				if (contact == null) {
					throw ApiException.badRequest("UNKNOWN_CONTACT",
							"Contact " + request.id() + " does not belong to this vendor");
				}
			}
			contact.setName(request.name().trim());
			contact.setRole(VendorText.trimToNull(request.role()));
			contact.setEmail(VendorText.email(request.email()));
			contact.setPhone(VendorText.trimToNull(request.phone()));
			contact.setPosition(i);
			next.add(contact);
		}
		vendor.getContacts().removeIf(c -> !next.contains(c));
		next.stream().filter(c -> !vendor.getContacts().contains(c)).forEach(vendor.getContacts()::add);
		vendor.getContacts().sort(Comparator.comparingInt(VendorContact::getPosition));
	}

	/** The fields an update audits; contacts as one readable line each. */
	private static Map<String, Object> snapshot(Vendor v) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.getName());
		values.put("taxId", v.getTaxId());
		values.put("category", v.getCategory());
		values.put("country", v.getCountry());
		values.put("address", v.getAddress());
		values.put("website", v.getWebsite());
		values.put("paymentTerms", v.getPaymentTerms());
		values.put("status", v.getStatus());
		values.put("rating", v.getRating());
		values.put("notes", v.getNotes());
		values.put("contacts", v.getContacts().stream().map(VendorService::describe).toList());
		return values;
	}

	private static String describe(VendorContact c) {
		List<String> details = new ArrayList<>();
		for (String detail : new String[] { c.getRole(), c.getEmail(), c.getPhone() }) {
			if (detail != null) {
				details.add(detail);
			}
		}
		return details.isEmpty() ? c.getName() : c.getName() + " (" + String.join(", ", details) + ")";
	}

	private Map<Long, VendorSummary> summaries(List<Long> ids) {
		Map<Long, VendorSummary> summaries = new HashMap<>();
		if (!ids.isEmpty()) {
			LocalDate today = calendar.today();
			contracts.summarise(ids, today).forEach(s -> summaries.put(s.getVendorId(), s));
		}
		return summaries;
	}

	private static VendorDto toDto(Vendor v, VendorSummary summary) {
		return new VendorDto(v.getId(), v.getName(), v.getTaxId(), v.getCategory(), v.getCountry(), v.getAddress(),
				v.getWebsite(), v.getPaymentTerms(), v.getStatus(), v.getRating(), v.getNotes(),
				v.getContacts().stream().map(ContactDto::of).toList(), summary == null ? 0 : summary.getContracts(),
				summary == null ? null : summary.getNextRenewal(), v.getVersion(), v.getCreatedAt(), v.getUpdatedAt());
	}

	private Vendor load(Long id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("VENDOR_NOT_FOUND", "Vendor not found"));
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

	private static Specification<Vendor> spec(VendorFilter f) {
		Specification<Vendor> spec = (root, query, cb) -> cb.conjunction();
		if (VendorText.trimToNull(f.search()) != null) {
			String pattern = VendorText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> {
				// Contacts through a subquery, so paging stays one row per vendor.
				Subquery<Long> contact = query.subquery(Long.class);
				var c = contact.from(VendorContact.class);
				contact.select(c.get("id"))
					.where(cb.equal(c.get("vendor"), root), cb.or(cb.like(cb.lower(c.get("name")), pattern, '\\'),
							cb.like(cb.lower(c.get("email")), pattern, '\\')));
				return cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
						cb.like(cb.lower(root.get("taxId")), pattern, '\\'),
						cb.like(cb.lower(root.get("country")), pattern, '\\'), cb.exists(contact));
			});
		}
		if (f.category() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), f.category()));
		}
		if (f.status() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
		}
		if (VendorText.trimToNull(f.country()) != null) {
			String country = f.country().trim().toLowerCase(Locale.ROOT);
			spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("country")), country));
		}
		return spec;
	}

}
