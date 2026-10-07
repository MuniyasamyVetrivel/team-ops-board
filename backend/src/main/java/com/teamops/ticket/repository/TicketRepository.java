package com.teamops.ticket.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.ticket.entity.Ticket;
import com.teamops.ticket.entity.TicketStatus;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

	/** Paged search; only to-one associations are fetched so paging stays in SQL. */
	@Override
	@EntityGraph(attributePaths = { "department", "category", "requester", "assignee" })
	Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "department", "category", "requester", "assignee", "slaPolicy" })
	Optional<Ticket> findDetailedById(Long id);

	/** Counts for "My Tickets" in one round trip. */
	@Query("""
			select
			  coalesce(sum(case when t.requester.id = :userId and t.status in :open then 1 else 0 end), 0) as requestedOpen,
			  coalesce(sum(case when t.requester.id = :userId
			    and t.status = com.teamops.ticket.entity.TicketStatus.WAITING_FOR_REQUESTER then 1 else 0 end), 0) as waitingOnMe,
			  coalesce(sum(case when t.requester.id = :userId
			    and t.status = com.teamops.ticket.entity.TicketStatus.RESOLVED then 1 else 0 end), 0) as resolvedToConfirm,
			  coalesce(sum(case when t.assignee.id = :userId and t.status in :open then 1 else 0 end), 0) as assignedOpen
			from Ticket t
			where t.requester.id = :userId or t.assignee.id = :userId
			""")
	MyTicketCounts countForUser(@Param("userId") Long userId, @Param("open") Collection<TicketStatus> open);

	/** Loads a bounded list for SLA computations (summary, dashboard); to-one associations included. */
	@EntityGraph(attributePaths = { "department", "category", "requester", "assignee" })
	List<Ticket> findAll(Specification<Ticket> spec);

}
