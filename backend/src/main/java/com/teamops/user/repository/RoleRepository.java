package com.teamops.user.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.user.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

	List<Role> findByCodeIn(Collection<String> codes);

}
