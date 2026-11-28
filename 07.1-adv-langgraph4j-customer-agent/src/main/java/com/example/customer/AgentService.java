
package com.example.customer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final ChatClient chatClient;

    public AgentService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public Map<String, Object> analyze(Map<String, Object> request) {

        String customerId = text(request.get("customerId"));
        String message = text(request.get("message"));

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("agent", "customer-agent");
        result.put("customerId", customerId);

        // Deterministic intent classification
        String intent = classifyIntent(message);

        result.put("intent", intent);
        result.put("nextRoute", intent);

        // LLM provides explanatory notes only.
        result.put("llmNotes", askLlm(message, intent));

        result.put("response",
                "Customer request classified as " + intent + ".");

        System.out.println("Customer ID: " + customerId);
        System.out.println("Customer Intent: " + intent);

        return result;
    }

    private String classifyIntent(String message) {

        String text = message.toLowerCase(Locale.ROOT);

        // Priority 1: Duplicate payment
        if (containsAny(text,
                "charged twice",
                "duplicate payment",
                "duplicate charge",
                "double charged",
                "charged two times",
                "paid twice")) {

            return "DUPLICATE_PAYMENT";
        }

        // Priority 2: General refund policy questions
        if (containsAny(text,
                "refund policy",
                "refund eligibility",
                "eligibility terms",
                "refund terms",
                "policy",
                "conditions",
                "eligibility")) {

            return "POLICY_QUESTION";
        }

        // Priority 3: Other billing questions
        if (containsAny(text,
                "payment",
                "charged",
                "charge",
                "billing",
                "invoice",
                "transaction",
                "subscription",
                "refund")) {

            return "PAYMENT_ISSUE";
        }

        return "GENERAL_QUERY";
    }

    private boolean containsAny(String text, String... keywords) {

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String askLlm(String message, String intent) {

        try {
            return chatClient.prompt()
                    .system("""
                        You are a customer support classification assistant.
                        Explain the identified intent briefly.
                        Do not invent customer, payment or subscription facts.
                        Do not approve or process refunds.
                        """)
                    .user("""
                        Customer message: %s
                        Deterministic intent: %s
                        """.formatted(message, intent))
                    .call()
                    .content();

        } catch (Exception ex) {
            return "LLM unavailable. Deterministic classification used.";
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
