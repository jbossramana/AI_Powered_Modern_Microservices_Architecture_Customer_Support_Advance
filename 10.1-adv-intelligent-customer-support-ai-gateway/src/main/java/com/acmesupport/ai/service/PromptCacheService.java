package com.acmesupport.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class PromptCacheService {
	private record Entry(String value, Instant expiry) {
	}

	private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
	private final LongAdder hits = new LongAdder(), misses = new LongAdder();
	@Value("${support.cache.ttl-seconds:600}")
	int ttl;
	@Value("${support.cache.max-entries:1000}")
	int max;

	public String key(String model, String prompt) {
		return model + "::" + prompt.strip().replaceAll("\\s+", " ").toLowerCase();
	}

	public String get(String key) {
		Entry e = map.get(key);
		if (e == null || e.expiry().isBefore(Instant.now())) {
			if (e != null)
				map.remove(key, e);
			misses.increment();
			return null;
		}
		hits.increment();
		return e.value();
	}

	public void put(String key, String value) {
		if (map.size() >= max)
			map.keySet().stream().findFirst().ifPresent(map::remove);
		map.put(key, new Entry(value, Instant.now().plusSeconds(ttl)));
	}

	public Stats stats() {
		map.entrySet().removeIf(e -> e.getValue().expiry().isBefore(Instant.now()));
		return new Stats(map.size(), hits.sum(), misses.sum(),
				hits.sum() + misses.sum() == 0 ? 0.0 : (double) hits.sum() / (hits.sum() + misses.sum()));
	}

	public record Stats(int entries, long hits, long misses, double hitRatio) {
	}
}
