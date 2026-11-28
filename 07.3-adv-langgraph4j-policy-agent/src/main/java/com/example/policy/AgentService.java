
package com.example.policy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AgentService {

    private final ChatClient chatClient;
    private final ObjectMapper mapper;

    public AgentService(
            ChatClient.Builder builder,
            ObjectMapper mapper) {

        this.chatClient = builder.build();
        this.mapper = mapper;
    }

    public Map<String, Object> analyze(
            Map<String, Object> request) {

        String customerId =
                text(request.get("customerId"));

        String message =
                text(request.get("message"));

        String billingContext =
                text(request.get("billingContext"));

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("agent", "policy-agent");
        result.put("customerId", customerId);

        // Parse actual Billing Agent result
        Map<String, Object> billingData =
                parseBillingContext(billingContext);

        boolean duplicatePayment =
                Boolean.TRUE.equals(
                        billingData.get("duplicatePayment"));

        boolean refundRequired =
                Boolean.TRUE.equals(
                        billingData.get("refundRequired"));

        // Deterministic demo policy evaluation
        boolean eligible =
                duplicatePayment && refundRequired;

        result.put("duplicatePayment", duplicatePayment);
        result.put("refundRequired", refundRequired);
        result.put("refundEligible", eligible);

        result.put("nextRoute", "POLICY_EVALUATED");

        // LLM is advisory, not the authority for eligibility.
        result.put(
                "llmNotes",
                "LLM output is advisory only. "
                + "Refund eligibility is determined by demo policy rules.");

        if (billingContext.isBlank()) {

            result.put(
                    "response",
                    "Refund policy: duplicate charges may qualify "
                    + "for a refund after transaction verification. "
                    + "Please provide the relevant transaction details.");

        } else if (eligible) {

            result.put(
                    "response",
                    "The Billing Agent identified a possible duplicate "
                    + "payment. The demo policy rules indicate that it "
                    + "qualifies for refund review. No refund has been "
                    + "processed by this sample application.");

        } else {

            result.put(
                    "response",
                    "The Billing Agent did not identify a duplicate "
                    + "payment eligible for refund under the demo rules.");
        }

        System.out.println(
                "========== POLICY EVALUATION ==========");

        System.out.println(
                "Duplicate payment: " + duplicatePayment);

        System.out.println(
                "Refund required: " + refundRequired);

        System.out.println(
                "Refund eligible: " + eligible);

        return result;
    }

    private Map<String, Object> parseBillingContext(
            String billingContext) {

        if (billingContext == null || billingContext.isBlank()) {
            return Map.of();
        }

        try {

            return mapper.readValue(
                    billingContext,
                    new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {

            System.err.println(
                    "Unable to parse billing context: "
                            + e.getMessage());

            return Map.of();
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
