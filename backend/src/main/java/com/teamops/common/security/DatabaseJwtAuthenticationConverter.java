package com.teamops.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Turns a verified JWT into an authentication whose roles and permissions come from the database, so disabling a
 * user or changing their permissions takes effect on their next request. Deliberately not a Spring bean: a
 * {@link Converter} bean would also be registered with Spring MVC's conversion service.
 */
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserPrincipalService userPrincipalService;

	public DatabaseJwtAuthenticationConverter(UserPrincipalService userPrincipalService) {
		this.userPrincipalService = userPrincipalService;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Long userId;
		try {
			userId = Long.valueOf(jwt.getSubject());
		}
		catch (NumberFormatException ex) {
			throw new InvalidBearerTokenException("Invalid token subject");
		}
		AuthenticatedUser user = userPrincipalService.findActiveUser(userId)
			.orElseThrow(() -> new InvalidBearerTokenException("User account is not active"));
		return new UserAuthenticationToken(user, jwt);
	}

}
