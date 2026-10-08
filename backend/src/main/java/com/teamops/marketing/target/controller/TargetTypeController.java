package com.teamops.marketing.target.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.marketing.target.dto.TargetDtos.CreateTargetType;
import com.teamops.marketing.target.dto.TargetDtos.TargetTypeItem;
import com.teamops.marketing.target.dto.TargetDtos.UpdateTargetType;
import com.teamops.marketing.target.service.TargetTypeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Target type administration (brief section 32): readable with TARGET_VIEW; Super Admins and marketing managers
 * (MARKETING_EDIT) add and change types.
 */
@RestController
@RequestMapping("/api/marketing/target-types")
@RequiredArgsConstructor
public class TargetTypeController {

	private final TargetTypeService typeService;

	@GetMapping
	@PreAuthorize("hasAuthority('TARGET_VIEW')")
	public List<TargetTypeItem> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
		return typeService.list(includeInactive);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public ResponseEntity<TargetTypeItem> create(@Valid @RequestBody CreateTargetType request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(typeService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public TargetTypeItem update(@PathVariable Long id, @Valid @RequestBody UpdateTargetType request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return typeService.update(id, request, actor, ClientInfo.from(http));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('MARKETING_EDIT')")
	public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		typeService.delete(id, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
