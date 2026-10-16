package com.teamops.vendor.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.security.FinanceAccess;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.vendor.VendorModule;
import com.teamops.vendor.dto.VendorDtos.ContractDto;
import com.teamops.vendor.dto.VendorDtos.DocumentDto;
import com.teamops.vendor.dto.VendorDtos.SaveContract;
import com.teamops.vendor.service.ContractRules.ContractStatus;
import com.teamops.vendor.service.VendorContractService;
import com.teamops.vendor.service.VendorContractService.ContractFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Vendor contracts and their documents. VENDOR_VIEW reads and downloads, VENDOR_EDIT changes and uploads; the module
 * switch applies to every endpoint. Contract values are shown, exported and sortable only with FINANCE_VIEW.
 */
@RestController
@RequestMapping(VendorModule.API + "/contracts")
@PreAuthorize("hasAuthority('VENDOR_VIEW')")
@RequiredArgsConstructor
public class VendorContractController {

	static final Map<String, List<String>> SORTS = Map.of("title", List.of("title", "id"), "vendor",
			List.of("vendor.name", "title", "id"), "start", List.of("startDate", "id"), "end", List.of("endDate", "id"),
			"renewal", List.of("renewalDate", "endDate", "id"), "value", List.of("value", "id"), "updated",
			List.of("updatedAt", "id"));

	/** Without FINANCE_VIEW the order of values would give them away, so sorting on them is refused (400). */
	static final Map<String, List<String>> SORTS_WITHOUT_VALUE = SORTS.entrySet()
		.stream()
		.filter(sort -> !sort.getKey().equals("value"))
		.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

	private final VendorContractService contractService;

	@GetMapping
	public PageResponse<ContractDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long vendorId, @RequestParam(required = false) ContractStatus status,
			@RequestParam(required = false) Integer renewalWithinDays, @RequestParam(required = false) Boolean autoRenew,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return contractService.search(new ContractFilter(search, vendorId, status, renewalWithinDays, autoRenew),
				PageRequests.of(page, size, sort, FinanceAccess.canView(viewer) ? SORTS : SORTS_WITHOUT_VALUE,
						"start,desc"),
				viewer);
	}

	/** CSV in the import's columns. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long vendorId, @RequestParam(required = false) ContractStatus status,
			@RequestParam(required = false) Integer renewalWithinDays, @RequestParam(required = false) Boolean autoRenew,
			@AuthenticationPrincipal AuthenticatedUser viewer) {
		return VendorDownloads.csv("vendor-contracts.csv",
				contractService.export(new ContractFilter(search, vendorId, status, renewalWithinDays, autoRenew), viewer));
	}

	@GetMapping("/{id}")
	public ContractDto get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return contractService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('VENDOR_EDIT')")
	public ResponseEntity<ContractDto> create(@Valid @RequestBody SaveContract request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(contractService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('VENDOR_EDIT')")
	public ContractDto update(@PathVariable Long id, @Valid @RequestBody SaveContract request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return contractService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('VENDOR_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		contractService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/documents")
	public List<DocumentDto> documents(@PathVariable Long id) {
		return contractService.documents(id);
	}

	@PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('VENDOR_EDIT')")
	public List<DocumentDto> addDocument(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return contractService.addDocument(id, file, actor, ClientInfo.from(http));
	}

	/** Always served as a download. */
	@GetMapping("/{id}/documents/{fileId}")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId) {
		return VendorDownloads.file(contractService.download(id, fileId));
	}

	@DeleteMapping("/{id}/documents/{fileId}")
	@PreAuthorize("hasAuthority('VENDOR_EDIT')")
	public List<DocumentDto> removeDocument(@PathVariable Long id, @PathVariable Long fileId,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return contractService.removeDocument(id, fileId, actor, ClientInfo.from(http));
	}

}
