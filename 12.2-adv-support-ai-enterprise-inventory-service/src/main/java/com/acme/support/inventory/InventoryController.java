package com.acme.support.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
	private static final Map<String, Map<String, Object>> reservations = new ConcurrentHashMap<>();

	@GetMapping("/reservations")
	public Map<String, Map<String, Object>> all() {
		return reservations;
	}

	@Component
	public static class InventoryEvents {
		private final Map<String, Map<String, Object>> reservations;
		private final KafkaTemplate<Object, Object> kafka;

		public InventoryEvents(KafkaTemplate<Object, Object> k) {
			this.kafka = k;
			this.reservations = InventoryController.reservations;
		}

		@KafkaListener(topics = "order.created", groupId = "inventory-service-group")
		public void orderCreated(Map<String, Object> e) {
			String id = (String) e.get("sagaId");
			Map<String, Object> event = new HashMap<>();
			event.put("sagaId", id);
			try {
				reservations.putIfAbsent(id, e);
				event.put("paymentToken", e.get("paymentToken"));
				event.put("amount", e.get("amount"));
				event.put("eventType", "InventoryReserved");
				kafka.send("inventory.reserved", id, event);
			} catch (Exception ex) {
				event.put("reason", ex.getMessage());
				kafka.send("inventory.rejected", id, event);
			}
		}

		@KafkaListener(topics = "order.cancelled", groupId = "inventory-service-group")
		public void orderCancelled(Map<String, Object> e) {
			String id = (String) e.get("sagaId");
			reservations.remove(id);
			kafka.send("inventory.released", id, Map.of("sagaId", id, "eventType", "InventoryReleased"));
		}
	}
}
