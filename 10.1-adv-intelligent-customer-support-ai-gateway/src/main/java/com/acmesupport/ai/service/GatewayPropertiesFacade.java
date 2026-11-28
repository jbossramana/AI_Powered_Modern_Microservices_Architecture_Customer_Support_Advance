package com.acmesupport.ai.service;

import com.acmesupport.ai.config.GatewayProperties;
import org.springframework.stereotype.Component;

@Component
public class GatewayPropertiesFacade {
	private final GatewayProperties props;

	public GatewayPropertiesFacade(GatewayProperties p) {
		props = p;
	}

	public String clientName(String key) {
		var c = key == null ? null : props.gateway().apiKeys().get(key);
		return c == null ? null : c.clientName();
	}

	public long dailyQuota(String key) {
		var c = key == null ? null : props.gateway().apiKeys().get(key);
		return c == null ? -1 : c.dailyTokenQuota();
	}
}
