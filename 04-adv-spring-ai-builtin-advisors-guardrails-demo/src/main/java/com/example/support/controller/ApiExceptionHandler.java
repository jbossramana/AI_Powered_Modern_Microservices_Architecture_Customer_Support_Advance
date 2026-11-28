package com.example.support.controller;

import com.example.support.guardrail.InputRejectedException;
import com.example.support.guardrail.OutputRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InputRejectedException.class)
    ResponseEntity<ApiError> inputRejected(InputRejectedException ex) {
        return error(HttpStatus.BAD_REQUEST, "INPUT_REJECTED", ex.getMessage());
    }

    @ExceptionHandler(OutputRejectedException.class)
    ResponseEntity<ApiError> outputRejected(OutputRejectedException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "OUTPUT_REJECTED", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), code, message));
    }

    public record ApiError(Instant timestamp, int status, String code, String message) {}
}
