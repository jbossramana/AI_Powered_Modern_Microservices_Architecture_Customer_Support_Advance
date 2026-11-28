package com.example.aigateway.web;
import com.example.aigateway.guardrail.GuardrailException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(GuardrailException.class)
 ResponseEntity<?> guardrail(GuardrailException ex){return ResponseEntity.unprocessableEntity().body(Map.of("timestamp",Instant.now().toString(),"error",ex.getCode(),"message",ex.getMessage()));}
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ResponseEntity<?> validation(MethodArgumentNotValidException ex){return ResponseEntity.badRequest().body(Map.of("error","VALIDATION_ERROR","message","Request validation failed"));}
 @ExceptionHandler(Exception.class)
 ResponseEntity<?> other(Exception ex){return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error","AI_GATEWAY_ERROR","message","The AI request could not be completed"));}
}