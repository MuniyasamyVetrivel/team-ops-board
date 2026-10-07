package com.teamops.common.security;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Loads the current user's identity and authorities from the database for each authenticated request. */
@Service
@RequiredArgsConstructor
public class UserPrincipalService {

	private final UserRepository userRepository;

	private final AuthenticatedUserFactory authenticatedUserFactory;

	/** Empty when the user no longer exists or has been disabled. */
	@Transactional(readOnly = true)
	public Optional<AuthenticatedUser> findActiveUser(Long userId) {
		return userRepository.findWithAuthoritiesById(userId)
			.filter(User::isActive)
			.map(authenticatedUserFactory::from);
	}

}
