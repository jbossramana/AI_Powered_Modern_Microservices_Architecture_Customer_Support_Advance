package com.example.orchestrator.graph;

import java.util.Map;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

public class SupportState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        "customerId", Channels.base(() -> ""),
        "message", Channels.base(() -> ""),
        "route", Channels.base(() -> ""),
        "customerResult", Channels.base(() -> ""),
        "billingResult", Channels.base(() -> ""),
        "policyResult", Channels.base(() -> ""),
        "finalResponse", Channels.base(() -> "")
    );

    public SupportState(Map<String, Object> initialData) {
        super(initialData);
    }

    public String text(String key) {
        return this.<String>value(key).orElse("");
    }
}
