package com.teamops.finance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.finance.entity.FinanceBudget;

public interface FinanceBudgetRepository extends JpaRepository<FinanceBudget, Long>, JpaSpecificationExecutor<FinanceBudget> {

	boolean existsByDepartmentIdAndYearAndMonth(Long departmentId, int year, int month);

	boolean existsByDepartmentIdAndYearAndMonthAndIdNot(Long departmentId, int year, int month, Long id);

	@EntityGraph(attributePaths = "department")
	List<FinanceBudget> findByYearAndMonth(int year, int month);

}
