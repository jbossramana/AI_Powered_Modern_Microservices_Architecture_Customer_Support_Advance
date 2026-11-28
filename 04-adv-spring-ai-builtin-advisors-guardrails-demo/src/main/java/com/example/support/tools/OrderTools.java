package com.example.support.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {

    @Tool(description = "Look up the current fulfilment status for an order. Input must be the exact order ID.")
    public String checkOrderStatus(String orderId) {
        validateOrderId(orderId);

        // Demo data. Replace with an authenticated order-service call.
        return switch (orderId) {
            case "O1001" -> "Order O1001 status: SHIPPED. Estimated delivery: 2026-11-04.";
            case "O1002" -> "Order O1002 status: PROCESSING.";
            default -> "No order record found for " + orderId + ".";
        };
    }

    public static void validateOrderId(String orderId) {
        if (orderId == null || !orderId.matches("O[0-9]{4}")) {
            throw new IllegalArgumentException(
                "Invalid order ID. Expected format O followed by four digits."
            );
        }
}
}
