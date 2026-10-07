package com.teamops.ticket.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import com.teamops.common.storage.StoredFile;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ticket_attachments")
public class TicketAttachment {

	@EmbeddedId
	private Id id;

	@MapsId("ticketId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ticket_id")
	private Ticket ticket;

	@MapsId("fileId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id")
	private StoredFile file;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "added_by")
	private User addedBy;

	@CreationTimestamp
	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	public static TicketAttachment of(Ticket ticket, StoredFile file, User addedBy) {
		TicketAttachment attachment = new TicketAttachment();
		attachment.setId(new Id(ticket.getId(), file.getId()));
		attachment.setTicket(ticket);
		attachment.setFile(file);
		attachment.setAddedBy(addedBy);
		return attachment;
	}

	@Getter
	@Embeddable
	@NoArgsConstructor
	public static class Id implements Serializable {

		@Column(name = "ticket_id")
		private Long ticketId;

		@Column(name = "file_id")
		private Long fileId;

		public Id(Long ticketId, Long fileId) {
			this.ticketId = ticketId;
			this.fileId = fileId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Id that && Objects.equals(ticketId, that.ticketId)
					&& Objects.equals(fileId, that.fileId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(ticketId, fileId);
		}

	}

}
