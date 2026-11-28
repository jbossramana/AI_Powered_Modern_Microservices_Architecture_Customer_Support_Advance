package com.acme.support.order;

import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
	private final SagaChoreography saga;

	public OrderController(SagaChoreography saga) {
		this.saga = saga;
	}

	@PostMapping
	public Map<String, Object> create(@RequestBody OrderRequest request) {
		return saga.start(request);
	}

	@GetMapping("/{sagaId}")
	public Map<String, Object> status(@PathVariable String sagaId) {
		return saga.status(sagaId);
	}

	@GetMapping("/health-flow")
	public Map<String, String> flow() {
		return Map.of("pattern", "CHOREOGRAPHY", "events",
				"OrderCreated -> InventoryReserved -> PaymentAuthorized -> OrderConfirmed", "compensation",
				"PaymentFailed -> OrderCancelled -> InventoryReleased");
	}

	public record OrderRequest(String customerId, String itemId, int quantity, BigDecimal amount, String paymentToken) {
	}
}
