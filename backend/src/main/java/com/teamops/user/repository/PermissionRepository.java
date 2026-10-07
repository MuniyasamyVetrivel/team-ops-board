package com.teamops.user.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.teamops.user.entity.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

	@Query("select p.code from Permission p")
	List<String> findAllCodes();

	List<Permission> findByCodeIn(Collection<String> codes);

}
