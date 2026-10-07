package com.teamops.common.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.teamops.common.exception.ErrorResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 401 as an ApiError JSON body (missing, invalid or expired token, or a disabled user). */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ErrorResponseWriter errorResponseWriter;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
				"Authentication required");
	}

}
