package com.teamops.marketing.target.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.web.ClientInfo;
import com.teamops.marketing.common.LeadSource;
import com.teamops.marketing.target.dto.TargetDtos.CreateTargetType;
import com.teamops.marketing.target.dto.TargetDtos.TargetTypeItem;
import com.teamops.marketing.target.dto.TargetDtos.TypeRef;
import com.teamops.marketing.target.dto.TargetDtos.UpdateTargetType;
import com.teamops.marketing.target.entity.ActualSource;
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.marketing.target.repository.MarketingTargetRepository;
import com.teamops.marketing.target.repository.TargetTypeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Configurable target types (brief section 32). Super Admins and marketing managers (MARKETING_EDIT) add more types.
 * Once a type has targets its unit and actual source are fixed, so historical months keep their meaning; a type in
 * use is deactivated rather than deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TargetTypeService {

	private static final String ENTITY = "MARKETING_TARGET_TYPE";

	private final TargetTypeRepository typeRepository;

	private final MarketingTargetRepository targetRepository;

	private final TargetActuals actuals;

	private final AppSettingsService settings;

	private final AuditService auditService;

	public List<TargetTypeItem> list(boolean includeInactive) {
		Map<Long, Long> counts = targetRepository.countByType()
			.stream()
			.collect(Collectors.toMap(MarketingTargetRepository.TypeCount::getTypeId,
					MarketingTargetRepository.TypeCount::getTotal));
		BigDecimal global = settings.marketingBehindThresholdPct();
		return typeRepository.findAllByOrderByPositionAscNameAsc()
			.stream()
			.filter(type -> includeInactive || type.isActive())
			.map(type -> toItem(type, counts.getOrDefault(type.getId(), 0L), global))
			.toList();
	}

	@Transactional
	public TargetTypeItem create(CreateTargetType request, AuthenticatedUser actor, ClientInfo client) {
		String name = request.name().trim();
		String code = StringUtils.hasText(request.code()) ? request.code().trim().toUpperCase(Locale.ROOT)
				: codeFrom(name);
		if (typeRepository.existsByCode(code)) {
			throw ApiException.conflict("DUPLICATE_CODE", "A target type with the code " + code + " already exists");
		}
		if (typeRepository.existsByName(name)) {
			throw duplicateName();
		}
		TargetType type = new TargetType();
		type.setCode(code);
		type.setName(name);
		type.setDescription(trimToNull(request.description()));
		type.setUnit(request.unit());
		type.setActualSource(request.actualSource());
		type.setLeadSourceFilter(leadSourceFor(request.actualSource(), request.leadSourceFilter()));
		type.setBehindThresholdPct(request.behindThresholdPct());
		type.setPosition(request.position() == null ? typeRepository.maxPosition() + 1 : request.position());
		TargetType saved = typeRepository.save(type);
		auditService.record(AuditAction.TARGET_TYPE_CREATED, actor.id(), ENTITY, saved.getId(),
				Map.of("code", code, "name", name), client);
		return toItem(saved, 0, settings.marketingBehindThresholdPct());
	}

	@Transactional
	public TargetTypeItem update(Long id, UpdateTargetType request, AuthenticatedUser actor, ClientInfo client) {
		TargetType type = load(id);
		if (!Objects.equals(type.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE",
					"Someone else changed this target type just now. Reload and try again.");
		}
		String name = request.name().trim();
		if (!name.equalsIgnoreCase(type.getName()) && typeRepository.existsByNameAndIdNot(name, id)) {
			throw duplicateName();
		}
		boolean inUse = targetRepository.existsByTypeId(id);
		if (inUse && (type.getUnit() != request.unit() || type.getActualSource() != request.actualSource())) {
			throw ApiException.conflict("TYPE_IN_USE",
					"This type already has targets, so its unit and actual source can no longer change");
		}
		LeadSource leadSource = leadSourceFor(request.actualSource(), request.leadSourceFilter());
		if (inUse && type.getLeadSourceFilter() != leadSource) {
			throw ApiException.conflict("TYPE_IN_USE",
					"This type already has targets, so its lead source can no longer change");
		}
		AuditChanges changes = new AuditChanges().track("name", type.getName(), name)
			.track("unit", type.getUnit(), request.unit())
			.track("actualSource", type.getActualSource(), request.actualSource())
			.track("leadSourceFilter", type.getLeadSourceFilter(), leadSource)
			.track("behindThresholdPct", type.getBehindThresholdPct(), request.behindThresholdPct())
			.track("active", type.isActive(), request.active())
			.track("position", type.getPosition(), request.position());
		type.setName(name);
		type.setDescription(trimToNull(request.description()));
		type.setUnit(request.unit());
		type.setActualSource(request.actualSource());
		type.setLeadSourceFilter(leadSource);
		type.setBehindThresholdPct(request.behindThresholdPct());
		type.setActive(request.active());
		type.setPosition(request.position());
		typeRepository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.TARGET_TYPE_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), client);
		}
		return toItem(type, inUse ? targetRepository.countByTypeId(id) : 0, settings.marketingBehindThresholdPct());
	}

	/** Only a type without targets can be deleted; a type in use is deactivated instead. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo client) {
		TargetType type = load(id);
		if (targetRepository.existsByTypeId(id)) {
			throw ApiException.conflict("TYPE_IN_USE",
					"This type has monthly targets. Deactivate it instead, so its history is kept.");
		}
		typeRepository.delete(type);
		auditService.record(AuditAction.TARGET_TYPE_DELETED, actor.id(), ENTITY, id,
				Map.of("code", type.getCode(), "name", type.getName()), client);
	}

	// --- shared with TargetService ----------------------------------------------------------------------------

	TargetType load(Long id) {
		return typeRepository.findById(id)
			.orElseThrow(() -> ApiException.notFound("TARGET_TYPE_NOT_FOUND", "Target type not found"));
	}

	/** The type's own threshold, else the global setting. */
	BigDecimal thresholdOf(TargetType type, BigDecimal global) {
		return type.getBehindThresholdPct() != null ? type.getBehindThresholdPct() : global;
	}

	TypeRef ref(TargetType type) {
		return new TypeRef(type.getId(), type.getCode(), type.getName(), type.getUnit(), type.getActualSource(),
				actuals.isAutomatic(type));
	}

	private TargetTypeItem toItem(TargetType type, long targetCount, BigDecimal global) {
		return new TargetTypeItem(type.getId(), type.getCode(), type.getName(), type.getDescription(), type.getUnit(),
				type.getActualSource(), type.getLeadSourceFilter(), type.getBehindThresholdPct(),
				thresholdOf(type, global), actuals.isAutomatic(type), type.isActive(), type.getPosition(), targetCount,
				targetCount > 0, type.getVersion());
	}

	/** LEADS_BY_SOURCE needs a lead source; every other source must not have one. */
	private static LeadSource leadSourceFor(ActualSource source, LeadSource leadSource) {
		if (source == ActualSource.LEADS_BY_SOURCE && leadSource == null) {
			throw ApiException.badRequest("LEAD_SOURCE_REQUIRED", "Choose which lead source this target counts");
		}
		return source == ActualSource.LEADS_BY_SOURCE ? leadSource : null;
	}

	/** "Webinar Sign-ups" → "WEBINAR_SIGN_UPS". */
	static String codeFrom(String name) {
		String code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (code.isEmpty() || !Character.isLetter(code.charAt(0))) {
			code = "TYPE_" + code;
		}
		return code.length() > 50 ? code.substring(0, 50) : code;
	}

	private static ApiException duplicateName() {
		return ApiException.conflict("DUPLICATE_NAME", "A target type with this name already exists");
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
