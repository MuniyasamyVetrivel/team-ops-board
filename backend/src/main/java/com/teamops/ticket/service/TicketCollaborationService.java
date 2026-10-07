package com.teamops.ticket.service;

import java.time.Instant;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.storage.FileService;
import com.teamops.common.storage.StoredFile;
import com.teamops.notification.entity.NotificationType;
import com.teamops.ticket.dto.TicketDtos.TicketDetail;
import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketAttachment;
import com.teamops.ticket.entity.TicketComment;
import com.teamops.ticket.entity.TicketStatus;
import com.teamops.ticket.repository.TicketAttachmentRepository;
import com.teamops.ticket.repository.TicketCommentRepository;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Replies, internal notes and attachments. Anyone who can see a ticket may reply publicly and attach files; only
 * agents may write internal notes.
 * <ul>
 * <li>The first public reply from an agent (anyone but the requester) is the first response: it stops that SLA
 * clock, and a NEW ticket becomes OPEN.</li>
 * <li>A reply from the requester while the ticket waits for them moves it back to OPEN, which resumes the clock.</li>
 * </ul>
 * Every method returns the refreshed ticket detail.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TicketCollaborationService {

	private final TicketService ticketService;

	private final TicketCommentRepository commentRepository;

	private final TicketAttachmentRepository attachmentRepository;

	private final UserRepository userRepository;

	private final FileService fileService;

	private final BusinessCalendar calendar;

	public TicketDetail addComment(Long ticketId, String body, boolean internal, AuthenticatedUser actor) {
		TicketAccess access = ticketService.access(actor);
		Ticket ticket = ticketService.loadVisible(ticketId, access);
		boolean agent = access.canWork(ticket);
		if (internal && !agent) {
			throw ApiException.forbidden("FORBIDDEN", "Only agents can add internal notes");
		}
		if (ticket.getStatus() == TicketStatus.CLOSED && !internal) {
			throw ApiException.badRequest("TICKET_CLOSED", "This ticket is closed. Reopen it to reply.");
		}
		TicketComment comment = new TicketComment();
		comment.setTicket(ticket);
		comment.setAuthor(userRepository.getReferenceById(actor.id()));
		comment.setBody(body.trim());
		comment.setInternal(internal);
		commentRepository.save(comment);

		if (!internal) {
			Instant now = calendar.now();
			if (ticket.isRequestedBy(actor.id())) {
				onRequesterReply(ticket, actor, now);
			}
			else if (agent) {
				onAgentReply(ticket, actor, now);
			}
		}
		return ticketService.toDetail(ticket, access);
	}

	/** Only the author may edit a reply. */
	public TicketDetail editComment(Long ticketId, Long commentId, String body, AuthenticatedUser actor) {
		TicketAccess access = ticketService.access(actor);
		Ticket ticket = ticketService.loadVisible(ticketId, access);
		TicketComment comment = loadComment(ticket, commentId, access);
		if (comment.getAuthor() == null || !comment.getAuthor().getId().equals(actor.id())) {
			throw ApiException.forbidden("FORBIDDEN", "You can only edit your own replies");
		}
		comment.setBody(body.trim());
		comment.setEdited(true);
		commentRepository.flush();
		return ticketService.toDetail(ticket, access);
	}

	/** The author, or an agent, may delete a reply. */
	public TicketDetail deleteComment(Long ticketId, Long commentId, AuthenticatedUser actor) {
		TicketAccess access = ticketService.access(actor);
		Ticket ticket = ticketService.loadVisible(ticketId, access);
		TicketComment comment = loadComment(ticket, commentId, access);
		boolean author = comment.getAuthor() != null && comment.getAuthor().getId().equals(actor.id());
		if (!author && !access.canWork(ticket)) {
			throw ApiException.forbidden("FORBIDDEN", "You cannot delete this reply");
		}
		commentRepository.delete(comment);
		return ticketService.toDetail(ticket, access);
	}

	public TicketDetail addAttachment(Long ticketId, MultipartFile upload, AuthenticatedUser actor) {
		TicketAccess access = ticketService.access(actor);
		Ticket ticket = ticketService.loadVisible(ticketId, access);
		StoredFile file = fileService.store(upload, actor.id());
		attachmentRepository.save(TicketAttachment.of(ticket, file, userRepository.getReferenceById(actor.id())));
		ticketService.history(ticket, actor, "attachment added", null, file.getOriginalName());
		return ticketService.toDetail(ticket, access);
	}

	@Transactional(readOnly = true)
	public Download download(Long ticketId, Long fileId, AuthenticatedUser actor) {
		TicketAccess access = ticketService.access(actor);
		ticketService.loadVisible(ticketId, access);
		StoredFile file = loadAttachment(ticketId, fileId).getFile();
		return new Download(file.getOriginalName(), file.getContentType(), file.getSizeBytes(),
				fileService.content(file));
	}

	/** The uploader, or an agent, may remove an attachment. */
	public TicketDetail deleteAttachment(Long ticketId, Long fileId, AuthenticatedUser actor) {
		TicketAccess access = ticketService.access(actor);
		Ticket ticket = ticketService.loadVisible(ticketId, access);
		TicketAttachment attachment = loadAttachment(ticketId, fileId);
		boolean uploader = attachment.getAddedBy() != null && attachment.getAddedBy().getId().equals(actor.id());
		if (!uploader && !access.canWork(ticket)) {
			throw ApiException.forbidden("FORBIDDEN", "You cannot remove this attachment");
		}
		String name = attachment.getFile().getOriginalName();
		attachmentRepository.delete(attachment);
		fileService.delete(attachment.getFile());
		ticketService.history(ticket, actor, "attachment removed", name, null);
		return ticketService.toDetail(ticket, access);
	}

	public record Download(String fileName, String contentType, long sizeBytes, Resource content) {
	}

	// --- helpers --------------------------------------------------------------------------------------------

	private void onAgentReply(Ticket ticket, AuthenticatedUser actor, Instant now) {
		if (ticket.getFirstRespondedAt() == null) {
			TicketService.stampFirstResponse(ticket, now);
			ticketService.history(ticket, actor, "first response", null, null);
		}
		if (ticket.getStatus() == TicketStatus.NEW) {
			TicketService.applyStatus(ticket, TicketStatus.OPEN, now);
			ticketService.history(ticket, actor, "status", TicketStatus.NEW.name(), TicketStatus.OPEN.name());
		}
		if (ticket.getRequester() != null) {
			ticketService.notify(ticket.getRequester().getId(), NotificationType.TICKET_REPLY,
					"New reply on " + ticket.getCode(), ticket);
		}
	}

	private void onRequesterReply(Ticket ticket, AuthenticatedUser actor, Instant now) {
		if (ticket.getStatus() == TicketStatus.WAITING_FOR_REQUESTER) {
			TicketService.applyStatus(ticket, TicketStatus.OPEN, now);
			ticketService.history(ticket, actor, "status", TicketStatus.WAITING_FOR_REQUESTER.name(),
					TicketStatus.OPEN.name());
		}
		if (ticket.getAssignee() != null && !ticket.isAssignedTo(actor.id())) {
			ticketService.notify(ticket.getAssignee().getId(), NotificationType.TICKET_REPLY,
					"New reply on " + ticket.getCode(), ticket);
		}
	}

	/** Internal notes are invisible (not found) to people who cannot work on the ticket. */
	private TicketComment loadComment(Ticket ticket, Long commentId, TicketAccess access) {
		return commentRepository.findById(commentId)
			.filter(comment -> comment.getTicket().getId().equals(ticket.getId()))
			.filter(comment -> !comment.isInternal() || access.canWork(ticket))
			.orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "Reply not found"));
	}

	private TicketAttachment loadAttachment(Long ticketId, Long fileId) {
		return attachmentRepository.findById(new TicketAttachment.Id(ticketId, fileId))
			.orElseThrow(() -> ApiException.notFound("ATTACHMENT_NOT_FOUND", "Attachment not found"));
	}

}
