package com.acme.support.payment;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
	private static final Map<String, String> ledger = new ConcurrentHashMap<>();

	@GetMapping("/ledger")
	public Map<String, String> ledger() {
		return ledger;
	}

	@Component
	public static class PaymentEvents {
		private final KafkaTemplate<Object, Object> kafka;

		public PaymentEvents(KafkaTemplate<Object, Object> k) {
			this.kafka = k;
		}

		@KafkaListener(topics = "inventory.reserved", groupId = "payment-service-group")
		public void inventoryReserved(Map<String, Object> e) {
			String id = (String) e.get("sagaId");
			Map<String, Object> result = new HashMap<>();
			result.put("sagaId", id);
			if ("FAIL".equals(e.get("paymentToken"))) {
				ledger.put(id, "DECLINED");
				result.put("eventType", "PaymentFailed");
				kafka.send("payment.failed", id, result);
			} else {
				ledger.put(id, "AUTHORIZED");
				result.put("eventType", "PaymentAuthorized");
				kafka.send("payment.authorized", id, result);
			}
		}
	}
}
