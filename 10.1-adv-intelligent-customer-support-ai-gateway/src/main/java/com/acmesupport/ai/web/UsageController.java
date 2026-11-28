package com.acmesupport.ai.web;

import com.acmesupport.ai.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {
	private final TokenUsageService usage;
	private final TokenQuotaService quota;
	private final GatewayPropertiesFacade facade;

	public UsageController(TokenUsageService u, TokenQuotaService q, GatewayPropertiesFacade f) {
		usage = u;
		quota = q;
		facade = f;
	}

	@GetMapping
	public Map<String, Object> summary() {
		return usage.summary();
	}

	@GetMapping("/requests")
	public List<TokenUsageService.Event> requests() {
		return usage.all();
	}

	@GetMapping("/clients")
	public Map<String, Object> clients() {
		return usage.byClient();
	}

	@GetMapping("/models")
	public Map<String, Object> models() {
		return usage.byModel();
	}

	@GetMapping("/daily")
	public List<Map<String, Object>> daily() {
		return usage.byDay();
	}

	@GetMapping("/monthly")
	public List<Map<String, Object>> monthly() {
		return usage.byMonth();
	}

	@GetMapping("/cost")
	public Map<String, Object> cost() {
		return usage.cost();
	}

	@GetMapping("/latency")
	public Map<String, Object> latency() {
		return usage.latency();
	}

	@GetMapping("/quota")
	public Map<String, Object> quota(@RequestHeader(value = "X-API-Key", required = false) String key) {
		String client = facade.clientName(key);
		if (client == null)
			return Map.of("error", "invalid_api_key");
		return Map.of("client", client, "remainingTokens", quota.remaining(key, client), "dailyQuota",
				facade.dailyQuota(key));
	}
}
