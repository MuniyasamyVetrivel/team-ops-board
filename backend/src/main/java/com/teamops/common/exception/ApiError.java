package com.teamops.common.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Uniform error body returned by every API endpoint. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String code,
		String message,
		String path,
		List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

}
