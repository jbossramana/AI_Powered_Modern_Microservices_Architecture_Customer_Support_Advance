package com.acmesupport.ai.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
	public record Request(@NotBlank String requestId, @Min(1) @Max(5) int rating, String comment) {
	}

	public record Entry(String requestId, int rating, String comment, Instant createdAt) {
	}

	private final ConcurrentLinkedDeque<Entry> entries = new ConcurrentLinkedDeque<>();

	@PostMapping
	public Map<String, String> submit(@Valid @RequestBody Request r) {
		entries.addFirst(new Entry(r.requestId(), r.rating(), r.comment(), Instant.now()));
		while (entries.size() > 500)
			entries.pollLast();
		return Map.of("status", "recorded", "requestId", r.requestId());
	}

	@GetMapping
	public List<Entry> list() {
		return List.copyOf(entries);
	}
}
