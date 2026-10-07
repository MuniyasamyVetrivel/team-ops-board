package com.teamops.sla.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.sla.entity.SlaPolicy;
import com.teamops.ticket.entity.TicketPriority;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

	Optional<SlaPolicy> findByPriority(TicketPriority priority);

	List<SlaPolicy> findAllByOrderByResolutionMinutesAsc();

}
