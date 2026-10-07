package com.teamops.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authentication whose principal is the database-resolved {@link AuthenticatedUser}. */
public class UserAuthenticationToken extends AbstractAuthenticationToken {

	private final AuthenticatedUser principal;

	private final transient Jwt jwt;

	public UserAuthenticationToken(AuthenticatedUser principal, Jwt jwt) {
		super(principal.authorities());
		this.principal = principal;
		this.jwt = jwt;
		setAuthenticated(true);
	}

	@Override
	public AuthenticatedUser getPrincipal() {
		return principal;
	}

	@Override
	public Jwt getCredentials() {
		return jwt;
	}

	@Override
	public String getName() {
		return principal.email();
	}

}
