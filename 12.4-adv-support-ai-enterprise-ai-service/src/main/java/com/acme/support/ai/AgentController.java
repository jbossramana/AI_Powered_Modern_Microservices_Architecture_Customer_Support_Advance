package com.acme.support.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
	private final ChatClient client;

	public AgentController(ChatClient.Builder builder, ToolCallbackProvider mcpTools) {
		this.client = builder.defaultToolCallbacks(mcpTools.getToolCallbacks()).build();
	}

	@PostMapping("/run")
	public Map<String, String> run(@RequestBody AgentRequest r) {
		String answer = client.prompt().system(
				"You are a goal-oriented customer-support agent. Goal: resolve or correctly triage the user's request. Use available MCP tools when useful. Ground decisions in tool results. Never claim an action succeeded unless a tool confirms it. If information is missing, ask for it. Keep actions bounded and do not issue refunds or change account data without explicit authorization.")
				.user(r.goal()).call().content();
		return Map.of("goal", r.goal(), "result", answer);
	}

	public record AgentRequest(String goal) {
	}
}
