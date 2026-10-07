package com.teamops.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.ticket.entity.TicketAttachment;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, TicketAttachment.Id> {

	@EntityGraph(attributePaths = { "file", "addedBy" })
	List<TicketAttachment> findByTicketIdOrderByAddedAtAsc(Long ticketId);

}
