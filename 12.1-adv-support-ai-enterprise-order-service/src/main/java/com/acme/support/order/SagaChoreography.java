
package com.acme.support.order;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SagaChoreography {

    private final KafkaTemplate<Object, Object> kafka;
    private final PolicyRules policy;

    // In-memory Saga state
    private final Map<String, Map<String, Object>> states =
            new ConcurrentHashMap<>();

    public SagaChoreography(
            KafkaTemplate<Object, Object> kafka,
            PolicyRules policy) {
        this.kafka = kafka;
        this.policy = policy;
    }

    // STEP 1: Start Saga
    public Map<String, Object> start(OrderController.OrderRequest r) {

        if (r.quantity() <= 0 || r.amount() == null
                || r.amount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity and amount must be positive");
        }

        String id = UUID.randomUUID().toString();

        // Drools business policy validation
        String decision = policy.evaluate(r.amount().doubleValue());

        Map<String, Object> state = new ConcurrentHashMap<>();

        state.put("sagaId", id);
        state.put("status", "PENDING");

        state.put("steps",
                Collections.synchronizedList(
                        new ArrayList<>(List.of(
                                "DROOLS_POLICY:" + decision))));

        states.put(id, state);

        // Manual review
        if ("MANUAL_REVIEW".equals(decision)) {
            state.put("status", "PENDING_REVIEW");
            return snapshot(state);
        }

        // Publish OrderCreated event
        Map<String, Object> event = new HashMap<>();

        event.put("eventType", "OrderCreated");
        event.put("sagaId", id);
        event.put("orderId", id);
        event.put("customerId", r.customerId());
        event.put("itemId", r.itemId());
        event.put("quantity", r.quantity());
        event.put("amount", r.amount());
        event.put("paymentToken",
                Objects.toString(r.paymentToken(), "demo-token"));

        publish("order.created", id, event, state);

        return snapshot(state);
    }

    // STEP 2: Payment authorized
    @KafkaListener(
            topics = "payment.authorized",
            groupId = "order-service-group")
    public void paymentAuthorized(Map<String, Object> event) {

        Map<String, Object> state = findState(event);

        if (state == null) return;

        synchronized (state) {

            if (!"PENDING".equals(state.get("status"))) return;

            state.put("status", "COMPLETED");

            add(state, "PAYMENT_AUTHORIZED");
            add(state, "ORDER_CONFIRMED");
        }
    }

    // STEP 3: Payment failed - initiate compensation
    @KafkaListener(
            topics = "payment.failed",
            groupId = "order-service-group")
    public void paymentFailed(Map<String, Object> event) {

        Map<String, Object> state = findState(event);

        if (state == null) return;

        synchronized (state) {

            if (!"PENDING".equals(state.get("status"))) return;

            state.put("status", "COMPENSATING");

            add(state, "PAYMENT_FAILED");

            Map<String, Object> cancel = new HashMap<>();

            cancel.put("eventType", "OrderCancelled");
            cancel.put("sagaId", event.get("sagaId"));
            cancel.put("orderId", event.get("orderId"));

            publish("order.cancelled",
                    Objects.toString(event.get("sagaId")),
                    cancel,
                    state);
        }
    }

    // STEP 4: Inventory compensation completed
    @KafkaListener(
            topics = "inventory.released",
            groupId = "order-service-group")
    public void inventoryReleased(Map<String, Object> event) {

        Map<String, Object> state = findState(event);

        if (state == null) return;

        synchronized (state) {

            if (!"COMPENSATING".equals(state.get("status"))) return;

            state.put("status", "COMPENSATED");

            add(state, "INVENTORY_RELEASED");
            add(state, "ORDER_COMPENSATED");
        }
    }

    // STEP 5: Inventory reservation rejected
    @KafkaListener(
            topics = "inventory.rejected",
            groupId = "order-service-group")
    public void inventoryRejected(Map<String, Object> event) {

        Map<String, Object> state = findState(event);

        if (state == null) return;

        synchronized (state) {

            if (!"PENDING".equals(state.get("status"))) return;

            state.put("status", "FAILED");

            add(state, "INVENTORY_REJECTED");
        }
    }

    // STEP 6: Get Saga status
    public Map<String, Object> status(String id) {

        Map<String, Object> state = states.get(id);

        if (state == null) {
            throw new NoSuchElementException(
                    "Unknown sagaId: " + id);
        }

        return snapshot(state);
    }

    // Kafka event publishing
    private void publish(
            String topic,
            String key,
            Map<String, Object> event,
            Map<String, Object> state) {

        kafka.send(topic, key, event)
                .whenComplete((result, error) -> {

                    if (error != null) {
                        synchronized (state) {
                            state.put("status", "FAILED");
                            add(state, "KAFKA_PUBLISH_FAILED:" + topic);
                        }
                        return;
                    }

                    synchronized (state) {
                        add(state, "PUBLISHED:" + topic);
                    }
                });
    }

    // Find Saga state
    private Map<String, Object> findState(
            Map<String, Object> event) {

        Object id = event.get("sagaId");

        if (id == null) return null;

        return states.get(id.toString());
    }

    // Add Saga execution step
    @SuppressWarnings("unchecked")
    private void add(Map<String, Object> state, String step) {

        List<String> steps = (List<String>) state.get("steps");

        synchronized (steps) {
            steps.add(step);
        }
    }

    // Return safe Saga snapshot
    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshot(
            Map<String, Object> state) {

        List<String> steps =
                (List<String>) state.get("steps");

        List<String> copy;

        synchronized (steps) {
            copy = new ArrayList<>(steps);
        }

        synchronized (state) {
            return Map.of(
                    "sagaId", state.get("sagaId"),
                    "status", state.get("status"),
                    "steps", copy
            );
        }
    }
}
