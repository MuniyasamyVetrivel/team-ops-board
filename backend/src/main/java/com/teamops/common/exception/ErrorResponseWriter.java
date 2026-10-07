package com.teamops.common.exception;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** Builds {@link ApiError} bodies; also used by the security filter chain, which runs outside Spring MVC. */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

	private final ObjectMapper objectMapper;

	private final Clock clock;

	public ApiError build(HttpStatus status, String code, String message, String path,
			List<ApiError.FieldError> fieldErrors) {
		return new ApiError(clock.instant(), status.value(), status.getReasonPhrase(), code, message, path,
				fieldErrors);
	}

	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
			String message) throws IOException {
		ApiError body = build(status, code, message, request.getRequestURI(), List.of());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), body);
	}

}
