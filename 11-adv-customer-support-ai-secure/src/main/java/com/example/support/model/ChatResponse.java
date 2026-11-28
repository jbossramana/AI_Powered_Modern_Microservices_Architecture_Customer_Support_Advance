package com.example.support.model;

import java.util.List;

public record ChatResponse(String answer, String requestId, int retrievedDocuments,
                           int estimatedPromptTokens, int estimatedAnswerTokens,
                           List<String> sources, String securityStatus) { }
