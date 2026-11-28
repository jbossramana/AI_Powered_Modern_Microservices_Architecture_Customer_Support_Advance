package com.example.support.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class StructuredSupportController {

    private final ChatClient chatClient;

    public StructuredSupportController(ChatClient supportChatClient) {
        this.chatClient = supportChatClient;
    }

    /**
     * Demonstrates Spring AI 2.0 structured-output schema validation.
     * validateSchema() enables StructuredOutputValidationAdvisor for this call;
     * if the JSON does not match the Java record schema, Spring AI retries with
     * validation feedback. This validates shape, not factual truth or PII.
     */
    @PostMapping("/structured-summary")
    public SupportSummary structuredSummary(@RequestBody SummaryRequest request) {

        return chatClient.prompt()
                .system("""
                        Return a concise support assessment.
                        riskLevel must be one of LOW, MEDIUM, HIGH.
                        nextAction must be a short recommended next step.
                        Do not claim that a refund or other transaction has been completed.
                        """)
                .user(request.question())
                .call()
                .entity(SupportSummary.class, spec -> spec.validateSchema());
    }

    public record SummaryRequest(String question) {}

    public record SupportSummary(
            String summary,
            String riskLevel,
            String nextAction) {}
}
