package com.example.mcpserver;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
@Component
public class OrderTools {
    @McpTool(description = "Look up the current status of an order for a customer")
    public String get_order_status(@McpToolParam(description = "Customer identifier") String customerId,
                                   @McpToolParam(description = "Order identifier") String orderId) {
        if ("C1001".equals(customerId) && "O1001".equals(orderId))
            return "{\"orderId\":\"O1001\",\"status\":\"DELIVERED\",\"amount\":1499,\"paymentStatus\":\"PAID\",\"duplicatePayment\":true}";
        return "{\"orderId\":\"" + orderId + "\",\"status\":\"PROCESSING\",\"paymentStatus\":\"PAID\",\"duplicatePayment\":false}";
    }
}
