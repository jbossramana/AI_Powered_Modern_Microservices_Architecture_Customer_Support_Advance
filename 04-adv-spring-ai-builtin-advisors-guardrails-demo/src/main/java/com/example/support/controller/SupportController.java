package com.example.support.controller;

import com.example.support.tools.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final ChatClient chatClient;

    public SupportController(ChatClient supportChatClient) {
        this.chatClient = supportChatClient;
    }

    @PostMapping("/{orderId}/investigate")
    public SupportResponse investigate(
            @PathVariable String orderId,
            @RequestBody SupportRequest request) {

        // Validate at the REST boundary before spending tokens or invoking tools.
        OrderTools.validateOrderId(orderId);

        String safeAnswer = chatClient.prompt()
                .system("""
                        You are a customer-support investigation assistant.
                        You have access to checkPaymentStatus and checkOrderStatus tools.
                        The exact order ID for this investigation is: %s
                        For every investigation, call both tools using that exact order ID.
                        Do not alter or invent the ID.
                        Base factual findings only on actual tool results.
                        Never claim that a refund was issued or a payment reversed.
                        You may explain that a human support representative must review
                        any refund request. Clearly distinguish tool findings from advice.
                        """.formatted(orderId))
                .user(request.question())
                .call()
                .content();

        // OutputGuardrailAdvisor already inspected/sanitized the response
        // inside the advisor chain before content() returns it.
        return new SupportResponse(orderId, safeAnswer);
    }

    public record SupportRequest(String question) {}
    public record SupportResponse(String orderId, String answer) {}
}
