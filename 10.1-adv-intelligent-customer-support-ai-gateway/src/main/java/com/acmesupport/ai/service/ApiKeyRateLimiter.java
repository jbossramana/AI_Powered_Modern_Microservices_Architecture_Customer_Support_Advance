package com.acmesupport.ai.service;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ApiKeyRateLimiter {
	private record Window(long minute, AtomicInteger count) {
	}

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public boolean tryConsume(String key, int limit) {
		long minute = Instant.now().getEpochSecond() / 60;
		Window w = windows.compute(key,
				(k, old) -> old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
		return w.count().incrementAndGet() <= limit;
	}
}
