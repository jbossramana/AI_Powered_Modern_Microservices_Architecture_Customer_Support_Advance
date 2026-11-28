package com.example.aigateway.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ChatRequest(@NotBlank @Size(max=8000) String message, String model, String conversationId) {}