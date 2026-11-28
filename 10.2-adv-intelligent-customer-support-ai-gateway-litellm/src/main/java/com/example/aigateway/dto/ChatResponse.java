package com.example.aigateway.dto;
public record ChatResponse(String answer,String model,String requestId,String guardrailStatus) {}