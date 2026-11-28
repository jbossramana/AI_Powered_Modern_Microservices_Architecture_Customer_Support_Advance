package com.acmesupport.ai.web;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<?> validation(MethodArgumentNotValidException e) {
		return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "error", "validation_failed",
				"message", "Check required fields and validation constraints."));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<?> general(Exception e) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("timestamp", Instant.now(), "error",
				"ai_request_failed", "message",
				"AI request failed. Check application logs and verify that Ollama is running with the configured model."));
	}
}
