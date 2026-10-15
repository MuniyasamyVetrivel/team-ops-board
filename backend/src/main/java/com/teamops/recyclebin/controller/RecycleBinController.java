package com.teamops.recyclebin.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.recyclebin.dto.RecycleBinDtos.Catalog;
import com.teamops.recyclebin.dto.RecycleBinDtos.Filter;
import com.teamops.recyclebin.dto.RecycleBinDtos.Item;
import com.teamops.recyclebin.dto.RecycleBinDtos.PurgeRequest;
import com.teamops.recyclebin.dto.RecycleBinDtos.Restored;
import com.teamops.recyclebin.service.RecycleBinService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The recycle bin: deleted records, newest first, filtered by module, record type, who deleted them, dates and name.
 * Super Admins see everything, Department Managers their departments' items (403 for anyone else). Restore by either;
 * permanent delete by a Super Admin, confirmed with the item's name.
 */
@RestController
@RequestMapping("/api/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("deleted", List.of("deletedAt", "id"), "name",
			List.of("label", "id"), "type", List.of("entityType", "deletedAt"));

	private final RecycleBinService recycleBin;

	@GetMapping
	public PageResponse<Item> list(@RequestParam(required = false) String module,
			@RequestParam(required = false) String entityType, @RequestParam(required = false) Long deletedById,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return recycleBin.list(new Filter(module, entityType, deletedById, from, to, search),
				PageRequests.of(page, size, sort, SORT_FIELDS, "deleted,desc"), viewer);
	}

	/** Modules, record types and people for the filters, and the retention period. */
	@GetMapping("/catalog")
	public Catalog catalog(@AuthenticationPrincipal AuthenticatedUser viewer) {
		return recycleBin.catalog(viewer);
	}

	@PostMapping("/{id}/restore")
	public Restored restore(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		return recycleBin.restore(id, actor);
	}

	@PostMapping("/{id}/purge")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	public ResponseEntity<Void> purge(@PathVariable Long id, @Valid @RequestBody PurgeRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		recycleBin.purge(id, request.confirm(), actor);
		return ResponseEntity.noContent().build();
	}

}
