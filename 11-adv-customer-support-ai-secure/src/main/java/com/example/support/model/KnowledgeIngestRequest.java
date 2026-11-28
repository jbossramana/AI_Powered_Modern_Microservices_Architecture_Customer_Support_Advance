package com.example.support.model;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeIngestRequest(@NotBlank String title, @NotBlank String content,
                                     @NotBlank String source) { }
