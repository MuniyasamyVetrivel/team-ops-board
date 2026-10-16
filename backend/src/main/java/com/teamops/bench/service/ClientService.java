package com.teamops.bench.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.bench.dto.BenchDtos.ClientDto;
import com.teamops.bench.dto.BenchDtos.SaveClient;
import com.teamops.bench.entity.Client;
import com.teamops.bench.entity.ClientStatus;
import com.teamops.bench.repository.ClientEngagementRepository;
import com.teamops.bench.repository.ClientEngagementRepository.ClientCount;
import com.teamops.bench.repository.ClientRepository;
import com.teamops.common.audit.AuditAction;
import com.teamops.common.audit.AuditChanges;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.service.RecycleBinService;

import lombok.RequiredArgsConstructor;

/**
 * Clients consultants are placed with (Phase X6). The name is unique, including clients in the recycle bin. A client
 * with live engagements cannot be deleted: delete or move those first. Changes are audited.
 */
@Service
@RequiredArgsConstructor
public class ClientService {

	public static final String ENTITY = "CLIENT";

	private final ClientRepository repository;

	private final ClientEngagementRepository engagements;

	private final AuditService auditService;

	private final RecycleBinService recycleBin;

	public record ClientFilter(String search, ClientStatus status) {
	}

	@Transactional(readOnly = true)
	public PageResponse<ClientDto> search(ClientFilter filter, Pageable pageable) {
		Page<Client> page = repository.findAll(spec(filter), pageable);
		Map<Long, Long> counts = new HashMap<>();
		if (page.hasContent()) {
			engagements.countByClient(page.map(Client::getId).getContent())
				.forEach(count -> counts.put(count.getClientId(), count.getTotal()));
		}
		return PageResponse.of(page.map(client -> toDto(client, counts.getOrDefault(client.getId(), 0L))));
	}

	@Transactional(readOnly = true)
	public ClientDto get(Long id) {
		return toDto(load(id));
	}

	@Transactional
	public ClientDto create(SaveClient request, AuthenticatedUser actor, ClientInfo info) {
		Client client = new Client();
		apply(client, request);
		Client saved = repository.saveAndFlush(client);
		auditService.record(AuditAction.CLIENT_CREATED, actor.id(), ENTITY, saved.getId(), Map.of("name", saved.getName()),
				info);
		return toDto(saved, 0);
	}

	@Transactional
	public ClientDto update(Long id, SaveClient request, AuthenticatedUser actor, ClientInfo info) {
		Client client = load(id);
		if (request.version() == null || !Objects.equals(client.getVersion(), request.version())) {
			throw ApiException.conflict("STALE_UPDATE", "Someone else changed this client just now. Reload and try again.");
		}
		Map<String, Object> before = snapshot(client);
		apply(client, request);
		AuditChanges changes = new AuditChanges();
		snapshot(client).forEach((field, to) -> changes.track(field, before.get(field), to));
		repository.flush();
		if (!changes.isEmpty()) {
			auditService.record(AuditAction.CLIENT_UPDATED, actor.id(), ENTITY, id, changes.toDetails(), info);
		}
		return toDto(client);
	}

	/** Moves the client to the recycle bin; 409 CLIENT_HAS_ENGAGEMENTS while it has live engagements. Audited. */
	@Transactional
	public void delete(Long id, AuthenticatedUser actor, ClientInfo info) {
		Client client = load(id);
		if (engagements.existsByClientId(id)) {
			throw ApiException.conflict("CLIENT_HAS_ENGAGEMENTS",
					client.getName() + " still has engagements. Delete them first.");
		}
		recycleBin.moveToBin(client, actor);
		auditService.record(AuditAction.CLIENT_DELETED, actor.id(), ENTITY, id, Map.of("name", client.getName()), info);
	}

	private void apply(Client client, SaveClient request) {
		String name = request.name().trim();
		boolean taken = client.getId() == null ? repository.existsByName(name)
				: repository.existsByNameAndIdNot(name, client.getId());
		if (taken) {
			throw ApiException.conflict("CLIENT_NAME_TAKEN", "Another client is already called " + name);
		}
		if (!name.equalsIgnoreCase(client.getName() == null ? "" : client.getName())) {
			recycleBin.requireNotInBin(Client.class, Map.of("name", name), "The client name " + name);
		}
		client.setName(name);
		client.setLocation(BenchText.trimToNull(request.location()));
		client.setStatus(request.status() == null ? ClientStatus.ACTIVE : request.status());
		client.setNotes(BenchText.trimToNull(request.notes()));
	}

	private static Map<String, Object> snapshot(Client client) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", client.getName());
		values.put("location", client.getLocation());
		values.put("status", client.getStatus());
		values.put("notes", client.getNotes());
		return values;
	}

	private ClientDto toDto(Client client) {
		long count = engagements.countByClient(List.of(client.getId()))
			.stream()
			.mapToLong(ClientCount::getTotal)
			.sum();
		return toDto(client, count);
	}

	private static ClientDto toDto(Client c, long engagementCount) {
		return new ClientDto(c.getId(), c.getName(), c.getLocation(), c.getStatus(), c.getNotes(), engagementCount,
				c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
	}

	private Client load(Long id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("CLIENT_NOT_FOUND", "Client not found"));
	}

	private static Specification<Client> spec(ClientFilter f) {
		Specification<Client> spec = (root, query, cb) -> cb.conjunction();
		if (BenchText.trimToNull(f.search()) != null) {
			String pattern = BenchText.containsPattern(f.search());
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
					cb.like(cb.lower(root.get("location")), pattern, '\\')));
		}
		if (f.status() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
		}
		return spec;
	}

}
