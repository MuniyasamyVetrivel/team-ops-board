package com.teamops.approval.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.approval.dto.ApprovalDtos;
import com.teamops.approval.dto.ApprovalDtos.ApprovalDetail;
import com.teamops.approval.dto.ApprovalDtos.ApprovalListItem;
import com.teamops.approval.dto.ApprovalDtos.TypeResponse;
import com.teamops.approval.entity.ApprovalStatus;
import com.teamops.approval.service.ApprovalService;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Approval API. Anyone with APPROVAL_VIEW can raise and follow requests; deciding needs APPROVAL_DECIDE plus being
 * the current step's approver (checked by the service); workflows need APPROVAL_CONFIGURE.
 */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('APPROVAL_VIEW')")
public class ApprovalController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("created", List.of("createdAt", "id"), "due",
			List.of("dueDate", "id"), "updated", List.of("updatedAt"), "code", List.of("id"));

	private final ApprovalService approvalService;

	@GetMapping("/types")
	public List<TypeResponse> types() {
		return approvalService.types();
	}

	@PostMapping("/types")
	@PreAuthorize("hasAuthority('APPROVAL_CONFIGURE')")
	public ResponseEntity<TypeResponse> createType(@Valid @RequestBody ApprovalDtos.CreateType request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(approvalService.createType(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/types/{id}")
	@PreAuthorize("hasAuthority('APPROVAL_CONFIGURE')")
	public TypeResponse updateType(@PathVariable Long id, @Valid @RequestBody ApprovalDtos.UpdateType request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return approvalService.updateType(id, request, actor, ClientInfo.from(http));
	}

	@PutMapping("/types/{id}/steps")
	@PreAuthorize("hasAuthority('APPROVAL_CONFIGURE')")
	public TypeResponse updateWorkflow(@PathVariable Long id, @Valid @RequestBody ApprovalDtos.UpdateWorkflow request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return approvalService.updateWorkflow(id, request, actor, ClientInfo.from(http));
	}

	@GetMapping
	public PageResponse<ApprovalListItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Set<ApprovalStatus> status,
			@RequestParam(required = false) Long typeId,
			@RequestParam(defaultValue = "ALL") ApprovalDtos.View view, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return approvalService.search(new ApprovalDtos.Search(search, status, typeId, view),
				PageRequests.of(page, size, sort, SORT_FIELDS, "created,desc"), actor);
	}

	@GetMapping("/{id}")
	public ApprovalDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		return approvalService.get(id, actor);
	}

	@PostMapping
	public ResponseEntity<ApprovalDetail> submit(@Valid @RequestBody ApprovalDtos.Submit request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(approvalService.submit(request, actor, ClientInfo.from(http)));
	}

	@PostMapping("/{id}/decision")
	@PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
	public ApprovalDetail decide(@PathVariable Long id, @Valid @RequestBody ApprovalDtos.Decide request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return approvalService.decide(id, request, actor, ClientInfo.from(http));
	}

	@PostMapping("/{id}/cancel")
	public ApprovalDetail cancel(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return approvalService.cancel(id, actor, ClientInfo.from(http));
	}

}
