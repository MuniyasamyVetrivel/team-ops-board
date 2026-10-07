package com.teamops.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.ticket.entity.TicketCategory;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

	@EntityGraph(attributePaths = "defaultDepartment")
	List<TicketCategory> findByActiveTrueOrderByNameAsc();

}
