package com.example.support.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Converts exceptions raised by the REST controller and the Spring AI
 * tool-calling pipeline into safe, consistent HTTP responses.
 *
 * A tool-policy SecurityException can be nested inside another exception
 * by the framework. Therefore, inspect the complete cause chain instead
 * of handling only a direct SecurityException.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path) {
    }

    /**
     * Handles direct and wrapped tool-policy violations, validation errors,
     * malformed JSON, and unexpected application errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(
            Exception exception,
            HttpServletRequest request) {

        SecurityException securityException =
                findCause(exception, SecurityException.class);

        if (securityException != null) {
            log.warn("Tool execution rejected by policy: {}",
                    securityException.getMessage());

            return response(
                    HttpStatus.FORBIDDEN,
                    "TOOL_EXECUTION_REJECTED",
                    "The requested tool operation was blocked by the security policy: "
                            + safeMessage(securityException),
                    request.getRequestURI());
        }

        if (exception instanceof MethodArgumentNotValidException validationException) {
            String details = validationException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(this::formatFieldError)
                    .collect(Collectors.joining("; "));

            return response(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    details.isBlank() ? "Request validation failed." : details,
                    request.getRequestURI());
        }

        if (exception instanceof HttpMessageNotReadableException) {
            return response(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST_BODY",
                    "The request body is missing or contains invalid JSON.",
                    request.getRequestURI());
        }

        log.error("Unhandled request failure at {}", request.getRequestURI(),
                exception);

        // Do not expose stack traces, internal class names, or tool arguments.
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The support request could not be completed. Please try again later.",
                request.getRequestURI());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String error,
            String message,
            String path) {

        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                error,
                message,
                path);

        return ResponseEntity.status(status).body(body);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": "
                + (error.getDefaultMessage() == null
                ? "invalid value"
                : error.getDefaultMessage());
    }

    private String safeMessage(SecurityException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Tool arguments were rejected."
                : message;
    }

    private <T extends Throwable> T findCause(
            Throwable exception,
            Class<T> expectedType) {

        Throwable current = exception;

        while (current != null) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }

            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }

        return null;
    }
}
