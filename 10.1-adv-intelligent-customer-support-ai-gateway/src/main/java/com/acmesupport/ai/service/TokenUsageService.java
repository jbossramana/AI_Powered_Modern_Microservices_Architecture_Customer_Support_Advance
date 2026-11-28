package com.acmesupport.ai.service;

import com.acmesupport.ai.config.GatewayProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

@Service
public class TokenUsageService {
	public record Event(String requestId, Instant timestamp, String client, String model, String sessionId,
			long promptTokens, long completionTokens, long totalTokens, long latencyMs, boolean cached, String status,
			double estimatedCost, boolean tokenUsageAvailable, String category) {
	}

	private final ConcurrentLinkedDeque<Event> events = new ConcurrentLinkedDeque<>();
	private final LongAdder totalRequests = new LongAdder(), errors = new LongAdder(), cacheHits = new LongAdder();
	private final GatewayProperties props;
	private final MeterRegistry registry;

	public TokenUsageService(GatewayProperties props, MeterRegistry registry) {
		this.props = props;
		this.registry = registry;
	}

	public synchronized void record(Event e) {
		events.addFirst(e);
		while (events.size() > props.usage().maxEvents())
			events.pollLast();
		totalRequests.increment();
		if (!"SUCCESS".equals(e.status()))
			errors.increment();
		if (e.cached())
			cacheHits.increment();
		registry.counter("support.requests", "client", e.client(), "status", e.status()).increment();
		registry.timer("support.request.duration", "status", e.status()).record(e.latencyMs(),
				java.util.concurrent.TimeUnit.MILLISECONDS);
		if (e.tokenUsageAvailable())
			registry.counter("support.tokens.total", "model", e.model()).increment(e.totalTokens());
		if (e.category() != null)
			registry.counter("support.tickets.classified", "category", e.category()).increment();
	}

	public List<Event> all() {
		return List.copyOf(events);
	}

	public Map<String, Object> summary() {
		long p = 0, c = 0, t = 0;
		double cost = 0;
		long known = 0;
		for (Event e : events) {
			p += e.promptTokens();
			c += e.completionTokens();
			t += e.totalTokens();
			cost += e.estimatedCost();
			if (e.tokenUsageAvailable())
				known++;
		}
		return Map.of("retainedEvents", events.size(), "totalRequestsSinceStart", totalRequests.sum(),
				"errorsSinceStart", errors.sum(), "cacheHitsSinceStart", cacheHits.sum(),
				"promptTokensInRetainedEvents", p, "completionTokensInRetainedEvents", c, "totalTokensInRetainedEvents",
				t, "estimatedCostInRetainedEvents", round(cost), "eventsWithProviderTokenUsage", known);
	}

	public Map<String, Object> byClient() {
		return aggregate("client");
	}

	public Map<String, Object> byModel() {
		return aggregate("model");
	}

	private Map<String, Object> aggregate(String dimension) {
		Map<String, long[]> a = new TreeMap<>();
		for (Event e : events) {
			String k = "client".equals(dimension) ? e.client() : e.model();
			long[] v = a.computeIfAbsent(k, x -> new long[4]);
			v[0] += e.promptTokens();
			v[1] += e.completionTokens();
			v[2] += e.totalTokens();
			v[3]++;
		}
		Map<String, Object> out = new LinkedHashMap<>();
		a.forEach((k, v) -> out.put(k,
				Map.of("promptTokens", v[0], "completionTokens", v[1], "totalTokens", v[2], "requests", v[3])));
		return out;
	}

	public List<Map<String, Object>> byDay() {
		return byPeriod(true);
	}

	public List<Map<String, Object>> byMonth() {
		return byPeriod(false);
	}

	private List<Map<String, Object>> byPeriod(boolean daily) {
		Map<String, long[]> a = new TreeMap<>();
		for (Event e : events) {
			String k = daily ? e.timestamp().atZone(ZoneId.systemDefault()).toLocalDate().toString()
					: YearMonth.from(e.timestamp().atZone(ZoneId.systemDefault())).toString();
			long[] v = a.computeIfAbsent(k, x -> new long[4]);
			v[0] += e.promptTokens();
			v[1] += e.completionTokens();
			v[2] += e.totalTokens();
			v[3]++;
		}
		List<Map<String, Object>> out = new ArrayList<>();
		a.forEach((k, v) -> out.add(Map.of("period", k, "promptTokens", v[0], "completionTokens", v[1], "totalTokens",
				v[2], "requests", v[3])));
		return out;
	}

	public Map<String, Object> cost() {
		double input = 0, output = 0;
		for (Event e : events) {
			input += e.promptTokens() * props.usage().inputCostPerMillion() / 1_000_000d;
			output += e.completionTokens() * props.usage().outputCostPerMillion() / 1_000_000d;
		}
		return Map.of("currency", "USD", "inputCost", round(input), "outputCost", round(output), "estimatedTotalCost",
				round(input + output), "pricingNote", "Illustrative configurable rates; not an Ollama invoice.");
	}

	private double round(double v) {
		return Math.round(v * 1_000_000d) / 1_000_000d;
	}

	public Map<String, Object> latency() {
		List<Long> values = events.stream().map(Event::latencyMs).sorted().toList();
		if (values.isEmpty())
			return Map.of("count", 0);
		return Map.of("count", values.size(), "averageMs",
				values.stream().mapToLong(Long::longValue).average().orElse(0), "p95Ms", percentile(values, .95),
				"p99Ms", percentile(values, .99));
	}

	private long percentile(List<Long> v, double p) {
		return v.get(Math.min(v.size() - 1, (int) Math.ceil(p * v.size()) - 1));
	}

	public long tokensToday(String client) {
		LocalDate today = LocalDate.now();
		return events.stream()
				.filter(e -> e.client().equals(client)
						&& e.timestamp().atZone(ZoneId.systemDefault()).toLocalDate().equals(today)
						&& e.tokenUsageAvailable())
				.mapToLong(Event::totalTokens).sum();
	}
}
