package com.teamops.notification.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.PageRequests;
import com.teamops.common.web.PageResponse;
import com.teamops.notification.dto.NotificationResponse;
import com.teamops.notification.dto.UnreadCount;
import com.teamops.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/** The signed-in user's own notifications. Any authenticated user may call it; there is no cross-user access. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	static final Map<String, List<String>> SORT_FIELDS = Map.of("created", List.of("createdAt", "id"));

	private final NotificationService notificationService;

	@GetMapping
	public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unread,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		return notificationService.list(actor, unread, PageRequests.of(page, size, null, SORT_FIELDS, "created,desc"));
	}

	@GetMapping("/unread-count")
	public UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedUser actor) {
		return notificationService.unreadCount(actor);
	}

	@PostMapping("/{id}/read")
	public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
		return notificationService.markRead(id, actor);
	}

	@PostMapping("/read-all")
	public UnreadCount markAllRead(@AuthenticationPrincipal AuthenticatedUser actor) {
		return notificationService.markAllRead(actor);
	}

}
