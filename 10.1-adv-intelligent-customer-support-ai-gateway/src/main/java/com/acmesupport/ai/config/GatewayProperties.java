package com.acmesupport.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "support")
public record GatewayProperties(Gateway gateway, Usage usage, Security security, Ai ai) {
	public record Gateway(Map<String, Client> apiKeys, int requestsPerMinute, int maxPromptChars) {
	}

	public record Client(String clientName, int requestsPerMinute, long dailyTokenQuota) {
	}

	public record Usage(int maxEvents, double inputCostPerMillion, double outputCostPerMillion) {
	}

	public record Security(boolean piiMasking, boolean promptInjectionCheck) {
	}

	public record Ai(String model) {
	}
}
