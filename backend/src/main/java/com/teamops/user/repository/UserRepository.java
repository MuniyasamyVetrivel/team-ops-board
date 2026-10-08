package com.teamops.user.repository;

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

import com.teamops.department.repository.DepartmentCount;
import com.teamops.user.entity.User;
import com.teamops.user.entity.UserStatus;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

	/** Loads the user with everything needed to resolve authorities, in a single query. */
	@EntityGraph(attributePaths = { "department", "roles", "roles.permissions", "directPermissions" })
	Optional<User> findWithAuthoritiesById(Long id);

	@EntityGraph(attributePaths = { "department", "roles", "roles.permissions", "directPermissions" })
	Optional<User> findWithAuthoritiesByEmailIgnoreCase(String email);

	/** Paged search. Only to-one associations are fetched here; roles are batch-loaded to keep paging in SQL. */
	@Override
	@EntityGraph(attributePaths = { "department", "reportsTo" })
	Page<User> findAll(Specification<User> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "department", "reportsTo" })
	Optional<User> findWithDepartmentById(Long id);

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

	@EntityGraph(attributePaths = "department")
	List<User> findByReportsToIdOrderByFirstNameAscLastNameAsc(Long managerId);

	@EntityGraph(attributePaths = "department")
	List<User> findByDepartmentIdOrderByFirstNameAscLastNameAsc(Long departmentId);

	@Query("select count(distinct u) from User u join u.roles r where r.code = :roleCode and u.status = :status")
	long countByRoleAndStatus(@Param("roleCode") String roleCode, @Param("status") UserStatus status);

	long countByDepartmentIdAndStatus(Long departmentId, UserStatus status);

	/** Active holders of a role (e.g. everyone who may decide a ROLE approval step). */
	@Query("select distinct u from User u join u.roles r where r.code = :roleCode and u.status = :status")
	List<User> findByRoleAndStatus(@Param("roleCode") String roleCode, @Param("status") UserStatus status);

	@Query("select u.department.id as departmentId, count(u) as total from User u where u.status = :status "
			+ "group by u.department.id")
	List<DepartmentCount> countByDepartment(@Param("status") UserStatus status);

}
