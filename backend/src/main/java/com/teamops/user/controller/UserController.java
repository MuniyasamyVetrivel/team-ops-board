package com.teamops.user.controller;

import java.util.List;
import java.util.Map;

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

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.user.dto.CreateUserRequest;
import com.teamops.user.dto.ResetPasswordRequest;
import com.teamops.user.dto.UpdateUserAccessRequest;
import com.teamops.user.dto.UpdateUserRequest;
import com.teamops.user.dto.UserDetail;
import com.teamops.user.dto.UserListItem;
import com.teamops.user.dto.UserSearchCriteria;
import com.teamops.user.entity.UserStatus;
import com.teamops.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("name", List.of("firstName", "lastName"), "email",
			List.of("email"), "department", List.of("department.name"), "lastLogin", List.of("lastLoginAt"),
			"created", List.of("createdAt"));

	private final UserService userService;

	@GetMapping
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public PageResponse<UserListItem> search(@RequestParam(required = false) String search,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) UserStatus status,
			@RequestParam(required = false) String role, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
		return userService.search(new UserSearchCriteria(search, departmentId, status, role),
				PageRequests.of(page, size, sort, SORT_FIELDS, "name,asc"));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public UserDetail get(@PathVariable Long id) {
		return userService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public ResponseEntity<UserDetail> create(@Valid @RequestBody CreateUserRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, actor, ClientInfo.from(http)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public UserDetail update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return userService.update(id, request, actor, ClientInfo.from(http));
	}

	@PutMapping("/{id}/access")
	@PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
	public UserDetail updateAccess(@PathVariable Long id, @Valid @RequestBody UpdateUserAccessRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		return userService.updateAccess(id, request, actor, ClientInfo.from(http));
	}

	@PostMapping("/{id}/disable")
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public UserDetail disable(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return userService.setStatus(id, UserStatus.DISABLED, actor, ClientInfo.from(http));
	}

	@PostMapping("/{id}/enable")
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public UserDetail enable(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest http) {
		return userService.setStatus(id, UserStatus.ACTIVE, actor, ClientInfo.from(http));
	}

	@PostMapping("/{id}/reset-password")
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
		userService.resetPassword(id, request, actor, ClientInfo.from(http));
		return ResponseEntity.noContent().build();
	}

}
