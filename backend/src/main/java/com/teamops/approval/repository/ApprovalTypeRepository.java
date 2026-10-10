package com.teamops.approval.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.approval.entity.ApprovalType;

public interface ApprovalTypeRepository extends JpaRepository<ApprovalType, Long> {

	@EntityGraph(attributePaths = { "steps", "steps.approverRole", "steps.approverUser" })
	List<ApprovalType> findAllByOrderByNameAsc();

	boolean existsByCode(String code);

}
