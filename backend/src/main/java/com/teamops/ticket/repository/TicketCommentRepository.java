package com.teamops.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.ticket.entity.TicketComment;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

	@EntityGraph(attributePaths = "author")
	List<TicketComment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

}
