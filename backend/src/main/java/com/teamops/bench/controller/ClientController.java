package com.teamops.bench.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.bench.BenchModule;
import com.teamops.bench.dto.BenchDtos.ClientDto;
import com.teamops.bench.dto.BenchDtos.SaveClient;
import com.teamops.bench.entity.ClientStatus;
import com.teamops.bench.service.ClientService;
import com.teamops.bench.service.ClientService.ClientFilter;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Bench Management clients. BENCH_VIEW reads, BENCH_EDIT changes; the module switch applies to every endpoint. */
@RestController
@RequestMapping(BenchModule.API + "/clients")
@PreAuthorize("hasAuthority('BENCH_VIEW')")
@RequiredArgsConstructor
public class ClientController {

	static final Map<String, List<String>> SORTS = Map.of("name", List.of("name", "id"), "updated",
			List.of("updatedAt", "id"));

	private final ClientService clientService;

	@GetMapping
	public PageResponse<ClientDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) ClientStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		return clientService.search(new ClientFilter(search, status), PageRequests.of(page, size, sort, SORTS, "name,asc"));
	}

	@GetMapping("/{id}")
	public ClientDto get(@PathVariable Long id) {
		return clientService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ResponseEntity<ClientDto> create(@Valid @RequestBody SaveClient request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ClientDto update(@PathVariable Long id, @Valid @RequestBody SaveClient request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return clientService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('BENCH_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		clientService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
