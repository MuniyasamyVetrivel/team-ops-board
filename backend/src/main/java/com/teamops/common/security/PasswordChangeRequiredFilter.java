package com.teamops.common.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.teamops.common.exception.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * While an admin-set password is still in use, the user may only read their profile and choose a new password; every
 * other API call is refused with 403 {@code PASSWORD_CHANGE_REQUIRED}. The flag is read from the database on each
 * request (see {@link DatabaseJwtAuthenticationConverter}), so it cannot be skipped by keeping an old token.
 * Deliberately not a Spring bean, so it is only registered in the security filter chain.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

	public static final String CODE = "PASSWORD_CHANGE_REQUIRED";

	static final Set<String> ALLOWED = Set.of("GET /api/auth/me", "POST /api/auth/change-password");

	private final ErrorResponseWriter errorResponseWriter;

	public PasswordChangeRequiredFilter(ErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
				&& user.passwordChangeRequired()
				&& !ALLOWED.contains(request.getMethod() + " " + request.getRequestURI())) {
			errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, CODE,
					"Choose a new password before continuing");
			return;
		}
		chain.doFilter(request, response);
	}

}
