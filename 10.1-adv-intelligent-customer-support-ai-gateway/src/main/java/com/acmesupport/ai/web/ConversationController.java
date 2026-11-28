package com.acmesupport.ai.web;

import com.acmesupport.ai.service.ConversationMemory;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/memory")
public class ConversationController {
	private final ConversationMemory memory;

	public ConversationController(ConversationMemory m) {
		memory = m;
	}

	@GetMapping("/stats")
	public Map<String, Integer> stats() {
		return Map.of("activeSessions", memory.sessionCount());
	}

	@DeleteMapping("/{sessionId}")
	public Map<String, String> clear(@PathVariable String sessionId) {
		memory.clear(sessionId);
		return Map.of("status", "cleared", "sessionId", sessionId);
	}
}
