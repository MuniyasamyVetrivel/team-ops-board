package com.teamops.module.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.module.entity.DepartmentModule;

public interface DepartmentModuleRepository extends JpaRepository<DepartmentModule, Long> {

	Optional<DepartmentModule> findByModuleKeyAndDepartmentId(String moduleKey, Long departmentId);

	/** Every switch that is on, with its department, for the Admin settings overview. */
	@EntityGraph(attributePaths = "department")
	List<DepartmentModule> findByEnabledTrue();

	/** Modules switched on for at least one active department (what a Super Admin can use). */
	@Query("select distinct dm.moduleKey from DepartmentModule dm where dm.enabled = true "
			+ "and dm.department.status = com.teamops.department.entity.DepartmentStatus.ACTIVE")
	Set<String> findEnabledKeys();

	/** Modules switched on for the user's primary department or any active department they are a member of. */
	@Query("select distinct dm.moduleKey from DepartmentModule dm where dm.enabled = true "
			+ "and dm.department.status = com.teamops.department.entity.DepartmentStatus.ACTIVE "
			+ "and (dm.department.id = :primaryDepartmentId or dm.department.id in "
			+ "(select m.id.departmentId from DepartmentMember m where m.id.userId = :userId))")
	Set<String> findEnabledKeysFor(@Param("userId") Long userId,
			@Param("primaryDepartmentId") Long primaryDepartmentId);

	/** Active departments a module is switched on for. */
	@Query("select dm.department.id from DepartmentModule dm where dm.moduleKey = :moduleKey and dm.enabled = true "
			+ "and dm.department.status = com.teamops.department.entity.DepartmentStatus.ACTIVE")
	Set<Long> findEnabledDepartmentIds(@Param("moduleKey") String moduleKey);

}
