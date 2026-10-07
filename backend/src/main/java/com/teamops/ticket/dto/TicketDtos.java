package com.teamops.ticket.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.teamops.department.dto.DepartmentSummary;
import com.teamops.sla.service.SlaCalculator;
import com.teamops.sla.service.SlaState;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketAttachment;
import com.teamops.ticket.entity.TicketCategory;
import com.teamops.ticket.entity.TicketComment;
import com.teamops.ticket.entity.TicketHistory;
import com.teamops.ticket.entity.TicketPriority;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.user.dto.UserSummary;

/** Response records for the ticket API. SLA values are computed per request and never stored. */
public final class TicketDtos {

	private TicketDtos() {
	}

	public record CategoryRef(Long id, String name) {

		public static CategoryRef of(TicketCategory category) {
			return category == null ? null : new CategoryRef(category.getId(), category.getName());
		}

	}

	/** Category with the team that handles it ({@code null}: the requester chooses). */
	public record CategoryResponse(Long id, String name, String description, DepartmentSummary defaultDepartment) {

		public static CategoryResponse of(TicketCategory category) {
			return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
					category.getDefaultDepartment() == null ? null
							: DepartmentSummary.of(category.getDefaultDepartment()));
		}

	}

	/** Both SLA deadlines and the worse of the two. */
	public record TicketSla(SlaCalculator.Status firstResponse, SlaCalculator.Status resolution, SlaState overall) {

		public static TicketSla of(Ticket ticket, Instant now) {
			SlaCalculator.Status first = SlaCalculator.evaluate(ticket.firstResponseClock(), now);
			SlaCalculator.Status resolution = SlaCalculator.evaluate(ticket.resolutionClock(), now);
			return new TicketSla(first, resolution, SlaState.worst(first.state(), resolution.state()));
		}

	}

	public record TicketListItem(Long id, String code, String subject, TicketStatus status, TicketPriority priority,
			CategoryRef category, DepartmentSummary department, UserSummary requester, UserSummary assignee,
			Instant createdAt, Instant updatedAt, TicketSla sla) {

		public static TicketListItem of(Ticket ticket, Instant now) {
			return new TicketListItem(ticket.getId(), ticket.getCode(), ticket.getSubject(), ticket.getStatus(),
					ticket.getPriority(), CategoryRef.of(ticket.getCategory()),
					DepartmentSummary.of(ticket.getDepartment()), UserSummary.of(ticket.getRequester()),
					UserSummary.of(ticket.getAssignee()), ticket.getCreatedAt(), ticket.getUpdatedAt(),
					TicketSla.of(ticket, now));
		}

	}

	public record CommentResponse(Long id, UserSummary author, String body, boolean internal, boolean edited,
			boolean fromRequester, Instant createdAt) {

		public static CommentResponse of(TicketComment comment, Ticket ticket) {
			boolean fromRequester = comment.getAuthor() != null && ticket.isRequestedBy(comment.getAuthor().getId());
			return new CommentResponse(comment.getId(), UserSummary.of(comment.getAuthor()), comment.getBody(),
					comment.isInternal(), comment.isEdited(), fromRequester, comment.getCreatedAt());
		}

	}

	public record AttachmentResponse(Long fileId, String fileName, String contentType, long sizeBytes,
			UserSummary addedBy, Instant addedAt) {

		public static AttachmentResponse of(TicketAttachment attachment) {
			return new AttachmentResponse(attachment.getFile().getId(), attachment.getFile().getOriginalName(),
					attachment.getFile().getContentType(), attachment.getFile().getSizeBytes(),
					UserSummary.of(attachment.getAddedBy()), attachment.getAddedAt());
		}

	}

	public record HistoryEntry(Long id, UserSummary changedBy, String field, String oldValue, String newValue,
			Instant changedAt) {

		public static HistoryEntry of(TicketHistory history) {
			return new HistoryEntry(history.getId(), UserSummary.of(history.getChangedBy()), history.getFieldName(),
					history.getOldValue(), history.getNewValue(), history.getChangedAt());
		}

	}

	/** What the viewer may do, so the UI can hide controls. The server re-checks every action. */
	public record TicketPermissions(boolean canWork, boolean canAssign, boolean canInternalNote,
			Set<TicketStatus> allowedStatuses) {

	}

	public record TicketDetail(Long id, String code, String subject, String description, TicketStatus status,
			TicketPriority priority, CategoryRef category, DepartmentSummary department, UserSummary requester,
			UserSummary assignee, String slaPolicy, TicketSla sla, Instant createdAt, Instant updatedAt,
			Instant firstRespondedAt, Instant resolvedAt, Instant closedAt, Integer version,
			List<CommentResponse> comments, List<AttachmentResponse> attachments, List<HistoryEntry> history,
			TicketPermissions permissions) {

	}

	public record MyTicketSummary(long requestedOpen, long waitingOnMe, long resolvedToConfirm, long assignedOpen) {

	}

}
