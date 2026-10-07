package com.teamops.department.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.department.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

	Optional<Department> findByCode(String code);

}
