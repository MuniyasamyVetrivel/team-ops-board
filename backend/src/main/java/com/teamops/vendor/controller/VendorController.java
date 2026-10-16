package com.teamops.vendor.controller;

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

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.vendor.VendorModule;
import com.teamops.vendor.dto.VendorDtos.SaveVendor;
import com.teamops.vendor.dto.VendorDtos.VendorDto;
import com.teamops.vendor.entity.VendorCategory;
import com.teamops.vendor.entity.VendorStatus;
import com.teamops.vendor.service.VendorLinkSource.LinkGroup;
import com.teamops.vendor.service.VendorService;
import com.teamops.vendor.service.VendorService.VendorFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Vendors and their contacts. VENDOR_VIEW reads, VENDOR_EDIT changes; the module switch applies to every endpoint
 * ({@code ModuleAccessInterceptor}). CSV import runs through {@code /api/modules/vendors/imports/vendors}.
 */
@RestController
@RequestMapping(VendorModule.API + "/vendors")
@PreAuthorize("hasAuthority('VENDOR_VIEW')")
@RequiredArgsConstructor
public class VendorController {

	static final Map<String, List<String>> SORTS = Map.of("name", List.of("name", "id"), "category",
			List.of("category", "name", "id"), "country", List.of("country", "name", "id"), "rating",
			List.of("rating", "name", "id"), "updated", List.of("updatedAt", "id"));

	private final VendorService vendorService;

	@GetMapping
	public PageResponse<VendorDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) VendorCategory category, @RequestParam(required = false) VendorStatus status,
			@RequestParam(required = false) String country, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		return vendorService.search(new VendorFilter(search, category, status, country),
				PageRequests.of(page, size, sort, SORTS, "name,asc"));
	}

	/** CSV in the import's columns, with each vendor's main contact. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) VendorCategory category, @RequestParam(required = false) VendorStatus status,
			@RequestParam(required = false) String country) {
		return VendorDownloads.csv("vendors.csv",
				vendorService.export(new VendorFilter(search, category, status, country)));
	}

	@GetMapping("/{id}")
	public VendorDto get(@PathVariable Long id) {
		return vendorService.get(id);
	}

	/** Records of other modules linked to the vendor (licences and expenses from Phase X8), as the viewer may see them. */
	@GetMapping("/{id}/links")
	public List<LinkGroup> links(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return vendorService.links(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('VENDOR_EDIT')")
	public ResponseEntity<VendorDto> create(@Valid @RequestBody SaveVendor request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(vendorService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('VENDOR_EDIT')")
	public VendorDto update(@PathVariable Long id, @Valid @RequestBody SaveVendor request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return vendorService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('VENDOR_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		vendorService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
