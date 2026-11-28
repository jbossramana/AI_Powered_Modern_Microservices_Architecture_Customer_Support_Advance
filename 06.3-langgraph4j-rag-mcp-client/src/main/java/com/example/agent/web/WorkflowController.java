
package com.example.agent.web;

import com.example.agent.graph.SupportState;
import com.example.agent.graph.XmlWorkflowLoader;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/support")
public class WorkflowController {

    private final CompiledGraph<SupportState> graph;

    // In-memory storage for workflows waiting for human approval.
    private final Map<String, Map<String, Object>> pendingApprovals =
            new ConcurrentHashMap<>();

    public WorkflowController(XmlWorkflowLoader loader) throws Exception {
        this.graph = loader.load();
    }

    // Customer request DTO
    public record SupportRequest(
            @NotBlank String customerId,
            @NotBlank String orderId,
            @NotBlank String question
    ) {}

    // Human approval DTO
    public record ApprovalRequest(
            @NotBlank String reviewer,
            @NotBlank String decision,
            String comment
    ) {}

    /**
     * Step 1: Receive customer request.
     * Step 2: Execute LangGraph4j XML-defined workflow.
     * Step 3: Return result or wait for human approval.
     */
    @PostMapping("/process")
    public Map<String, Object> process(
            @Valid @RequestBody SupportRequest request) throws Exception {

        Map<String, Object> initialState = Map.of(
                "customerId", request.customerId(),
                "orderId", request.orderId(),
                "question", request.question()
        );

        // LangGraph4j 1.9.7 invocation API.
        Optional<SupportState> result = graph.invoke(
                GraphInput.args(initialState),
                RunnableConfig.empty()
        );

        SupportState finalState = result.orElseThrow(
                () -> new IllegalStateException(
                        "LangGraph4j returned no final state")
        );

        String runId = UUID.randomUUID().toString();

        // Preserve the complete workflow result.
        Map<String, Object> state = new HashMap<>();

        state.put("runId", runId);
        state.put("customerId", finalState.str("customerId"));
        state.put("orderId", finalState.str("orderId"));
        state.put("question", finalState.str("question"));
        state.put("knowledge", finalState.str("knowledge"));
        state.put("orderDetails", finalState.str("orderDetails"));
        state.put("decision", finalState.str("decision"));
        state.put("approvalStatus", finalState.str("approvalStatus"));
        state.put("approvalComment", finalState.str("approvalComment"));
        state.put("answer", finalState.str("answer"));

        // Check whether human approval is required.
        if ("PENDING".equalsIgnoreCase(
                finalState.str("approvalStatus"))) {

            pendingApprovals.put(runId, state);

            Map<String, Object> response = new LinkedHashMap<>();

            response.put("runId", runId);
            response.put("status", "AWAITING_HUMAN_APPROVAL");
            response.put("customerId", finalState.str("customerId"));
            response.put("orderId", finalState.str("orderId"));
            response.put("decision", finalState.str("decision"));
            response.put("approvalStatus", "PENDING");
            response.put("knowledge", finalState.str("knowledge"));
            response.put("orderDetails", finalState.str("orderDetails"));
            response.put("message",
                    "Supervisor review is required. No refund has been executed.");

            return response;
        }

        // Information-only request: no human approval required.
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("runId", runId);
        response.put("status", "COMPLETED");
        response.put("customerId", finalState.str("customerId"));
        response.put("orderId", finalState.str("orderId"));
        response.put("decision", finalState.str("decision"));
        response.put("approvalStatus", finalState.str("approvalStatus"));
        response.put("answer", finalState.str("answer"));

        return response;
    }

    /**
     * Human approval endpoint.
     *
     * APPROVED or REJECTED.
     * Each pending workflow can be reviewed only once.
     */
    @PostMapping("/{runId}/approval")
    public Map<String, Object> approve(
            @PathVariable String runId,
            @Valid @RequestBody ApprovalRequest approval) {

        // Validate decision before modifying pending state.
        String decision = approval.decision().trim()
                .toUpperCase(Locale.ROOT);

        if (!Set.of("APPROVED", "REJECTED").contains(decision)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "decision must be APPROVED or REJECTED"
            );
        }

        // Atomic removal prevents duplicate approval submissions.
        Map<String, Object> state = pendingApprovals.remove(runId);

        if (state == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Pending workflow not found or already reviewed"
            );
        }

        String comment = approval.comment() == null
                ? ""
                : approval.comment();

        String approvalComment =
                "Reviewer: " + approval.reviewer()
                + "; Decision: " + decision
                + "; Comment: " + comment;

        String answer =
                "Customer: " + state.get("customerId")
                + "\nOrder: " + state.get("orderId")
                + "\nOriginal decision: " + state.get("decision")
                + "\nHuman approval: " + decision
                + "\nPolicy context: " + state.get("knowledge")
                + "\nOrder details: " + state.get("orderDetails")
                + "\n" + approvalComment
                + "\nNo refund has been executed by this demo.";

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("runId", runId);
        response.put("status", "COMPLETED");
        response.put("customerId", state.get("customerId"));
        response.put("orderId", state.get("orderId"));
        response.put("decision", state.get("decision"));
        response.put("approvalStatus", decision);
        response.put("reviewer", approval.reviewer());
        response.put("comment", comment);
        response.put("answer", answer);

        return response;
    }
}
