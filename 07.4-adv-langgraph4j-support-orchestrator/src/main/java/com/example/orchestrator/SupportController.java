package com.example.orchestrator;

import java.util.Map;

import com.example.orchestrator.graph.SupportGraphBuilder;
import com.example.orchestrator.model.SupportRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportGraphBuilder graphBuilder;

    public SupportController(SupportGraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
    }

    @PostMapping
    public Map<String, Object> support(@RequestBody SupportRequest request) {
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        return graphBuilder.run(request.customerId(), request.message());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "support-orchestrator");
    }
}
