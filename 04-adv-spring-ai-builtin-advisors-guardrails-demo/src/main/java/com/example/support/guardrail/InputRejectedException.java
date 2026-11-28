package com.example.support.guardrail;

public class InputRejectedException extends RuntimeException {
    public InputRejectedException(String message) {
        super(message);
    }
}
