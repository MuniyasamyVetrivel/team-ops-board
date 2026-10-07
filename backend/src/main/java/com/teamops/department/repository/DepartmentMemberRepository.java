package com.teamops.department.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamops.department.entity.DepartmentMember;
import com.teamops.department.entity.DepartmentMemberId;
import com.teamops.department.entity.DepartmentMemberRole;

public interface DepartmentMemberRepository extends JpaRepository<DepartmentMember, DepartmentMemberId> {

	@Query("select m.id.departmentId from DepartmentMember m where m.id.userId = :userId and m.memberRole = :role")
	Set<Long> findDepartmentIds(@Param("userId") Long userId, @Param("role") DepartmentMemberRole role);

	@EntityGraph(attributePaths = { "user", "user.department" })
	List<DepartmentMember> findByIdDepartmentId(Long departmentId);

	@Query("select m.id.departmentId as departmentId, count(m) as total from DepartmentMember m "
			+ "where m.user.status = com.teamops.user.entity.UserStatus.ACTIVE group by m.id.departmentId")
	List<DepartmentCount> countActiveByDepartment();

}
