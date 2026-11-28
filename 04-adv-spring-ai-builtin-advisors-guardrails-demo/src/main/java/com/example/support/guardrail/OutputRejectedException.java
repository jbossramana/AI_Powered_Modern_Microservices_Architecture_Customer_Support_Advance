package com.example.support.guardrail;

public class OutputRejectedException extends RuntimeException {
    public OutputRejectedException(String message) {
        super(message);
    }
}
