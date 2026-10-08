package com.teamops.announcement.dto;

import java.time.Instant;

import com.teamops.announcement.entity.AnnouncementPriority;
import com.teamops.announcement.entity.AnnouncementState;
import com.teamops.department.dto.DepartmentSummary;
import com.teamops.user.dto.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Announcement API records. Read and acknowledgement rates are computed per request. */
public final class AnnouncementDtos {

	private AnnouncementDtos() {
	}

	/** Shown to people who can manage the announcement. {@code audience} = active users it is addressed to. */
	public record Stats(long audience, long read, long acknowledged) {

	}

	/**
	 * @param read whether the viewer has opened it
	 * @param acknowledged whether the viewer has acknowledged it ({@code ackRequired} only)
	 */
	public record AnnouncementItem(Long id, String title, String body, AnnouncementPriority priority,
			DepartmentSummary targetDepartment, Instant publishAt, Instant expiresAt, boolean ackRequired,
			UserSummary createdBy, AnnouncementState state, boolean read, boolean acknowledged, Stats stats,
			boolean canManage, Integer version) {

	}

	public record UnreadCount(long unread, long awaitingAcknowledgement) {

	}

	/**
	 * {@code publishAt = null} publishes now. {@code targetDepartmentId = null} is for everyone (Super Admin).
	 * {@code ackRequired} may be omitted (false).
	 */
	public record Save(
			Integer version,
			@NotBlank(message = "Title is required") @Size(max = 200) String title,
			@NotBlank(message = "Message is required") @Size(max = 20000) String body,
			Long targetDepartmentId,
			@NotNull(message = "Priority is required") AnnouncementPriority priority,
			Instant publishAt,
			Instant expiresAt,
			Boolean ackRequired) {

		public boolean requiresAck() {
			return Boolean.TRUE.equals(ackRequired);
		}

	}

}
