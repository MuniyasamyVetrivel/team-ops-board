package com.teamops.bench.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.teamops.bench.entity.Consultant;

/** Skills and certifications of a page are batch-loaded (default_batch_fetch_size). */
public interface ConsultantRepository extends JpaRepository<Consultant, Long>, JpaSpecificationExecutor<Consultant> {

	@Override
	@EntityGraph(attributePaths = { "employee", "employee.department", "employee.user" })
	Page<Consultant> findAll(Specification<Consultant> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = { "employee", "employee.department", "employee.user" })
	List<Consultant> findAll(Specification<Consultant> spec, Sort sort);

	@EntityGraph(attributePaths = { "employee", "employee.department", "employee.user" })
	Optional<Consultant> findDetailedById(Long id);

	boolean existsByEmployeeId(Long employeeId);

	boolean existsByEmployeeIdAndIdNot(Long employeeId, Long id);

}
