package com.teamops.department.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.dto.CreateDepartmentRequest;
import com.teamops.department.dto.DepartmentDetail;
import com.teamops.department.dto.DepartmentListItem;
import com.teamops.department.dto.DepartmentMemberRequest;
import com.teamops.department.dto.UpdateDepartmentRequest;
import com.teamops.department.service.DepartmentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

	private final DepartmentService departmentService;

	/** Every signed-in user can list departments (filters, pickers, team directory). */
	@GetMapping
	public List<DepartmentListItem> list() {
		return departmentService.list();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('TEAM_VIEW', 'DEPARTMENT_MANAGE')")
	public DepartmentDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
		return departmentService.get(id, viewer);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public ResponseEntity<DepartmentDetail> create(@Valid @RequestBody CreateDepartmentRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(departmentService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
	public DepartmentDetail update(@PathVariable Long id, @Valid @RequestBody UpdateDepartmentRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return departmentService.update(id, request, actor, ClientInfo.from(http));
	}

	/** Department managers may manage members of their own departments; the service enforces the scope. */
	@PutMapping("/{id}/members/{userId}")
	public DepartmentDetail upsertMember(@PathVariable Long id, @PathVariable Long userId,
			@Valid @RequestBody DepartmentMemberRequest request, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return departmentService.upsertMember(id, userId, request.role(), actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}/members/{userId}")
	public DepartmentDetail removeMember(@PathVariable Long id, @PathVariable Long userId,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return departmentService.removeMember(id, userId, actor, ClientInfo.from(http));
	}

}
