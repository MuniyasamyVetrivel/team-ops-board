package com.teamops.celebration.controller;

import java.util.List;
import java.util.Map;

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

import com.teamops.celebration.dto.CelebrationDtos.EmployeeDto;
import com.teamops.celebration.dto.CelebrationDtos.SaveEmployee;
import com.teamops.celebration.entity.EmployeeStatus;
import com.teamops.celebration.service.EmployeeService;
import com.teamops.celebration.service.EmployeeService.EmployeeFilter;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Employees for birthdays and work anniversaries. Digital Marketing users (MARKETING_VIEW) read active employees (the service hides birth
 * years and people who left from them); CELEBRATIONS_EDIT changes and exports. CSV import runs through
 * {@code /api/marketing/imports/employees}.
 */
@RestController
@PreAuthorize("hasAuthority('MARKETING_VIEW')")
@RequestMapping("/api/celebrations/employees")
@RequiredArgsConstructor
public class EmployeeController {

	static final Map<String, List<String>> SORTS = Map.of("name", List.of("firstName", "lastName", "id"), "code",
			List.of("employeeCode", "id"), "joined", List.of("dateOfJoining", "id"), "updated",
			List.of("updatedAt", "id"));

	private final EmployeeService employeeService;

	@GetMapping
	public PageResponse<EmployeeDto> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) EmployeeStatus status, @RequestParam(required = false) Long departmentId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return employeeService.search(new EmployeeFilter(search, status, departmentId),
				PageRequests.of(page, size, sort, SORTS, "name,asc"), viewer);
	}

	/** CSV in the import's columns, with full birth dates. */
	@GetMapping("/export")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
			@RequestParam(required = false) EmployeeStatus status, @RequestParam(required = false) Long departmentId) {
		return CelebrationDownloads.csv("employees.csv",
				employeeService.export(new EmployeeFilter(search, status, departmentId)));
	}

	@GetMapping("/{id}")
	public EmployeeDto get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return employeeService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public ResponseEntity<EmployeeDto> create(@Valid @RequestBody SaveEmployee request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public EmployeeDto update(@PathVariable Long id, @Valid @RequestBody SaveEmployee request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return employeeService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		employeeService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

	/** Always served as a download. */
	@GetMapping("/{id}/photo")
	public ResponseEntity<Resource> photo(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return CelebrationDownloads.file(employeeService.photo(id, viewer));
	}

	@PutMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public EmployeeDto setPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return employeeService.setPhoto(id, file, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}/photo")
	@PreAuthorize("hasAuthority('CELEBRATIONS_EDIT')")
	public EmployeeDto deletePhoto(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return employeeService.deletePhoto(id, actor, ClientInfo.from(http));
	}

}
