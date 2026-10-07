package com.teamops.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.ticket.entity.TicketHistory;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

	@EntityGraph(attributePaths = "changedBy")
	List<TicketHistory> findTop100ByTicketIdOrderByChangedAtDescIdDesc(Long ticketId);

}
