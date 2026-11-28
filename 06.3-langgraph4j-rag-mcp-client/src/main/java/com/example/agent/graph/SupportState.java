package com.example.agent.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.Map;

public class SupportState extends AgentState {

    // LangGraph4j 1.9.7 requires a Supplier<T> for Channels.base(...).
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "question", Channels.base(() -> ""),
            "customerId", Channels.base(() -> ""),
            "orderId", Channels.base(() -> ""),
            "knowledge", Channels.base(() -> ""),
            "orderDetails", Channels.base(() -> ""),
            "decision", Channels.base(() -> "PENDING"),
            "approvalStatus", Channels.base(() -> "NOT_REQUIRED"),
            "approvalComment", Channels.base(() -> ""),
            "answer", Channels.base(() -> "")
    );

    public SupportState(Map<String, Object> initData) {
        super(initData);
    }

    public String str(String key) {
        return this.<String>value(key).orElse("");
    }
}
