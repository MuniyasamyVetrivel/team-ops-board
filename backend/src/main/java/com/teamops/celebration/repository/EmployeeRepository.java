package com.teamops.celebration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.entity.EmployeeStatus;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

	@Override
	@EntityGraph(attributePaths = { "department", "user" })
	Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = { "department", "user" })
	List<Employee> findAll(Specification<Employee> spec, Sort sort);

	@EntityGraph(attributePaths = { "department", "user", "photo" })
	Optional<Employee> findDetailedById(Long id);

	/** Everyone whose birthdays and anniversaries are celebrated (a few hundred rows at most). */
	@EntityGraph(attributePaths = { "department", "user" })
	List<Employee> findByStatus(EmployeeStatus status);

	boolean existsByEmployeeCode(String employeeCode);

	boolean existsByEmployeeCodeAndIdNot(String employeeCode, Long id);

	Optional<Employee> findByUserId(Long userId);

	@EntityGraph(attributePaths = "user")
	List<Employee> findByUserIdIsNotNull();

}
