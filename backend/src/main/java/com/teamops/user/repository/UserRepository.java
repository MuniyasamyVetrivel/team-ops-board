package com.teamops.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.teamops.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	/** Loads the user with everything needed to resolve authorities, in a single query. */
	@EntityGraph(attributePaths = { "department", "roles", "roles.permissions", "directPermissions" })
	Optional<User> findWithAuthoritiesById(Long id);

	@EntityGraph(attributePaths = { "department", "roles", "roles.permissions", "directPermissions" })
	Optional<User> findWithAuthoritiesByEmailIgnoreCase(String email);

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

}
