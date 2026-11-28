package com.example.support.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class PaymentTools {

    @Tool(description = "Look up payment status for an order. Input must be the exact order ID.")
    public String checkPaymentStatus(String orderId) {
        OrderTools.validateOrderId(orderId);

        // Demo data. Replace with an authenticated payment-service call.
        return switch (orderId) {
            case "O1001" -> "Payment for O1001: SUCCESS. Amount: INR 2499.00. One captured payment found.";
            case "O1002" -> "Payment for O1002: PENDING. No captured payment found yet.";
            default -> "No payment record found for " + orderId + ".";
        };
    }
}
