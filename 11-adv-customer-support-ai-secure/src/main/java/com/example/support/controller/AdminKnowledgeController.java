package com.example.support.controller;

import com.example.support.model.KnowledgeIngestRequest;
import com.example.support.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {
    private final KnowledgeBaseService knowledgeBase;
    public AdminKnowledgeController(KnowledgeBaseService knowledgeBase) { this.knowledgeBase = knowledgeBase; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> submit(@Valid @RequestBody KnowledgeIngestRequest request, Authentication auth) {
        String id = knowledgeBase.submitForReview(request.title(), request.content(), request.source(), auth.getName());
        return Map.of("documentId", id, "status", "PENDING_REVIEW",
            "message", "Not searchable until a separate approval action is completed.");
    }

    @PostMapping("/{id}/approve")
    public Map<String, String> approve(@PathVariable String id, Authentication auth) {
        try {
            knowledgeBase.approve(id, auth.getName());
            return Map.of("documentId", id, "status", "APPROVED");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping("/pending-count")
    public Map<String, Integer> pendingCount() { return Map.of("pendingDocuments", knowledgeBase.pendingCount()); }
}
