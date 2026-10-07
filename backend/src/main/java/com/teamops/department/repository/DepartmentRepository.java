package com.teamops.department.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.department.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

	Optional<Department> findByCode(String code);

	@EntityGraph(attributePaths = "manager")
	List<Department> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = "manager")
	Optional<Department> findWithManagerById(Long id);

	@Query("select d.id from Department d where d.manager.id = :userId")
	Set<Long> findIdsManagedBy(@Param("userId") Long userId);

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

}
