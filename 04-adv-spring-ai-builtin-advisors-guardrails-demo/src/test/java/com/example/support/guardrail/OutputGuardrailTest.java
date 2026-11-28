package com.example.support.guardrail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutputGuardrailTest {

    private final OutputGuardrail guardrail = new OutputGuardrail();

    @Test
    void masksEmailAndPhone() {
        String result = guardrail.sanitize("Contact user@example.com at +91 98765 43210");
        assertFalse(result.contains("user@example.com"));
        assertFalse(result.contains("98765 43210"));
        assertTrue(result.contains("[REDACTED_EMAIL]"));
        assertTrue(result.contains("[REDACTED_PHONE]"));
    }

    @Test
    void rejectsUnsupportedRefundPromise() {
        assertThrows(OutputRejectedException.class,
                () -> guardrail.sanitize("Your refund has been issued."));
    }
}
