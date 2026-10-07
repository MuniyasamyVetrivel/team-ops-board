package com.teamops.ticket.entity;

import com.teamops.common.persistence.BaseEntity;
import com.teamops.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A reply on a ticket. Internal notes are visible to agents only. */
@Getter
@Setter
@Entity
@Table(name = "ticket_comments")
public class TicketComment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ticket_id", nullable = false)
	private Ticket ticket;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id")
	private User author;

	@Column(name = "body", nullable = false, columnDefinition = "text")
	private String body;

	@Column(name = "is_internal", nullable = false)
	private boolean internal;

	@Column(name = "edited", nullable = false)
	private boolean edited;

}
