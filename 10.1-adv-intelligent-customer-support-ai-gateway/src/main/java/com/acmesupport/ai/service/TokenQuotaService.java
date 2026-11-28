package com.acmesupport.ai.service;

import com.acmesupport.ai.config.GatewayProperties;
import org.springframework.stereotype.Service;

@Service
public class TokenQuotaService {
	private final GatewayProperties props;
	private final TokenUsageService usage;

	public TokenQuotaService(GatewayProperties p, TokenUsageService u) {
		props = p;
		usage = u;
	}

	public boolean allowed(String apiKey, String clientName) {
		var c = props.gateway().apiKeys().get(apiKey);
		return c == null || c.dailyTokenQuota() <= 0 || usage.tokensToday(clientName) < c.dailyTokenQuota();
	}

	public long remaining(String apiKey, String clientName) {
		var c = props.gateway().apiKeys().get(apiKey);
		if (c == null || c.dailyTokenQuota() <= 0)
			return -1;
		return Math.max(0, c.dailyTokenQuota() - usage.tokensToday(clientName));
	}
}
