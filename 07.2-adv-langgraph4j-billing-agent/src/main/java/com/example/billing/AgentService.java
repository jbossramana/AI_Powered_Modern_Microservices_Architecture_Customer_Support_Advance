package com.example.billing;

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
        result.put("agent", "billing-agent");
        result.put("customerId", customerId);

        String llmNotes = askLlm(request);
        result.put("llmNotes", llmNotes);

        String normalized = message.toLowerCase(Locale.ROOT);
        boolean duplicate = normalized.contains("twice")
                || normalized.contains("duplicate")
                || normalized.contains("charged two times");
        boolean refundRequired = duplicate || normalized.contains("refund");
        result.put("duplicatePayment", duplicate);
        result.put("refundRequired", refundRequired);
        result.put("nextRoute", refundRequired ? "REFUND_REQUIRED" : "NO_REFUND_REQUIRED");
        result.put("response", duplicate
                ? "A possible duplicate payment was identified. Refund policy review is required."
                : "No duplicate payment was identified by the demo billing rule.");
        return result;
    }

    private String askLlm(Map<String, Object> request) {
        String message = text(request.get("message"));
        String context = request.toString();
        try {
            return chatClient.prompt()
                    .system("You are a specialist support agent. Analyze the supplied facts carefully. Do not claim a payment was actually reversed or a refund was actually issued.")
                    .user("Customer support request: " + message + "\\nContext: " + context)
                    .call()
                    .content();
        } catch (Exception ex) {
            return "LLM unavailable; deterministic demo rule used. Details: " + ex.getClass().getSimpleName();
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
