package com.teamops.common.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Maps every exception to an {@link ApiError}. Internal details are logged, never returned. */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	private final ErrorResponseWriter errors;

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
		return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), request, List.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<ApiError.FieldError> fieldErrors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
			.toList();
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiError> handleInvalidParameters(HandlerMethodValidationException ex,
			HttpServletRequest request) {
		List<ApiError.FieldError> fieldErrors = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(error -> new ApiError.FieldError(result.getMethodParameter().getParameterName(),
						error.getDefaultMessage())))
			.toList();
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fieldErrors);
	}

	/** Wrong type for a path or query parameter, e.g. {@code ?status=SLEEPING} or {@code /api/users/abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
				"Invalid value for parameter '" + ex.getName() + "'", request,
				List.of(new ApiError.FieldError(ex.getName(), "Invalid value")));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
				"Missing required parameter '" + ex.getParameterName() + "'", request, List.of());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex,
			HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "The file is larger than the allowed upload size",
				request, List.of());
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ApiError> handleMultipart(MultipartException ex, HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "The upload could not be read", request, List.of());
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex,
			HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, "MISSING_FILE", "No file was uploaded", request, List.of());
	}

	/** Two people saved the same record at once; the later save must reload first. */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
			HttpServletRequest request) {
		return respond(HttpStatus.CONFLICT, "STALE_UPDATE",
				"Someone else changed this just now. Reload and try again.", request, List.of());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed", request,
				List.of());
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", request, List.of());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action",
				request, List.of());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
		return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request, List.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request,
				List.of());
	}

	private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message,
			HttpServletRequest request, List<ApiError.FieldError> fieldErrors) {
		return ResponseEntity.status(status)
			.body(errors.build(status, code, message, request.getRequestURI(), fieldErrors));
	}

}
