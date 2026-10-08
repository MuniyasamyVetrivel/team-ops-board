package com.teamops.ticket.dto;

import java.util.Set;

import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies and search criteria for the ticket API. */
public final class TicketRequests {

	private TicketRequests() {
	}

	/**
	 * The requester is always the caller. The handling department comes from the category, or from
	 * {@code departmentId} when the category has none.
	 * @param assigneeId optional; needs the right to assign in the handling department
	 */
	public record CreateTicket(
			@NotBlank(message = "Subject is required") @Size(max = 250) String subject,
			@Size(max = 20000) String description,
			@NotNull(message = "Category is required") Long categoryId,
			Long departmentId,
			TicketPriority priority,
			Long assigneeId) {

	}

	/**
	 * Agent edit. Priority changes do not move SLA due times, which are fixed at creation. Moving the ticket to
	 * another department unassigns it when the assignee is not in that department.
	 */
	public record UpdateTicket(
			@NotNull(message = "Version is required") Integer version,
			@NotBlank(message = "Subject is required") @Size(max = 250) String subject,
			@Size(max = 20000) String description,
			@NotNull(message = "Category is required") Long categoryId,
			@NotNull(message = "Department is required") Long departmentId,
			@NotNull(message = "Priority is required") TicketPriority priority) {

	}

	/** {@code assigneeId = null} unassigns. */
	public record Assign(Long assigneeId) {

	}

	public record ChangeStatus(@NotNull(message = "Status is required") TicketStatus status) {

	}

	/** {@code internal} notes are for agents only; omitted means a public reply. */
	public record Comment(@NotBlank(message = "Reply cannot be empty") @Size(max = 10000) String body,
			Boolean internal) {

		public boolean isInternal() {
			return Boolean.TRUE.equals(internal);
		}

	}

	public record EditComment(@NotBlank(message = "Reply cannot be empty") @Size(max = 10000) String body) {

	}

	/** Optional filters; null or empty means "any". */
	public record Search(String search, Set<TicketStatus> statuses, Set<TicketPriority> priorities, Long categoryId,
			Long departmentId, Long assigneeId, TicketView view) {

		public Search {
			statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
			priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
			view = view == null ? TicketView.ALL : view;
		}

	}

}
