package com.acmesupport.ai.web;

import com.acmesupport.ai.config.GatewayProperties;
import com.acmesupport.ai.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ChatController {
	private final SupportAiService ai;
	private final GatewayProperties props;
	private final ApiKeyRateLimiter limiter;
	private final PromptCacheService cache;
	private final TokenUsageService usage;
	private final UsageMetadataReader reader;
	private final TokenQuotaService quota;
	private final InputGuardService guard;
	private final String model;

	public ChatController(SupportAiService ai, GatewayProperties props, ApiKeyRateLimiter limiter,
			PromptCacheService cache, TokenUsageService usage, UsageMetadataReader reader, TokenQuotaService quota,
			InputGuardService guard, @Value("${spring.ai.ollama.chat.options.model}") String model) {
		this.ai = ai;
		this.props = props;
		this.limiter = limiter;
		this.cache = cache;
		this.usage = usage;
		this.reader = reader;
		this.quota = quota;
		this.guard = guard;
		this.model = model;
	}

	public record ChatRequest(@NotBlank String message, String sessionId) {
	}

	public record ChatResponse(String requestId, String reply, boolean cached, String model, String sessionId,
			long promptTokens, long completionTokens, long totalTokens, boolean tokenUsageAvailable, long latencyMs) {
	}

	public record ClassifyRequest(@NotBlank String message) {
	}

	@PostMapping("/chat")
	public ResponseEntity<?> chat(@RequestHeader(value = "X-API-Key", required = false) String key,
			@Valid @RequestBody ChatRequest req) {
		long start = System.currentTimeMillis();
		
		// Step 1: Authenticate the API client using the API key.
		var client = client(key);
		
		if (client == null)
			return ResponseEntity.status(401).body(Map.of("error", "invalid_api_key"));
		
		// Step 2: Apply rate limiting to prevent excessive API requests.
		if (!limiter.tryConsume(key, client.requestsPerMinute()))
			return ResponseEntity.status(429)
					.body(Map.of("error", "rate_limit_exceeded", "limitPerMinute", client.requestsPerMinute()));
		
		 // Step 3: Check whether the client has exceeded its daily token quota.
		if (!quota.allowed(key, client.clientName()))
			return ResponseEntity.status(429).body(Map.of("error", "daily_token_quota_exceeded", "remainingTokens", 0));
		
		   // Step 4: Validate the maximum allowed prompt size.
		if (req.message().length() > props.gateway().maxPromptChars())
			return ResponseEntity.badRequest()
					.body(Map.of("error", "prompt_too_large", "maxChars", props.gateway().maxPromptChars()));
		
        // Step 5: Sanitize user input before sending it to the LLM.
        // InputGuardService masks sensitive information such as email,
        // phone numbers and card-like numbers.
        // It also checks for configured prompt-injection phrases.
        // Only the sanitized message should be passed to the LLM.
		String safe;
		try {
			safe = guard.sanitize(req.message());
		} catch (InputGuardService.UnsafePromptException e) {
			return ResponseEntity.status(400).body(Map.of("error", "unsafe_prompt", "message", e.getMessage()));
		}
		
        // Step 6: Determine whether this request is stateless or stateful.
        // No sessionId -> Stateless: previous conversation history is not
        // associated with this request.
        // sessionId provided -> Stateful: the same sessionId can be used
        // to retrieve previous conversation history through ChatMemory.
		boolean stateless = req.sessionId() == null || req.sessionId().isBlank();
		
        // Step 7: Generate a cache key using the model and sanitized prompt.
        // This avoids caching the original input containing sensitive data.
		String cacheKey = cache.key(model, safe);
		
		// Generate a unique identifier for tracking this API request.
		String requestId = "REQ-" + UUID.randomUUID();
		
        // Step 8: Check prompt cache only for stateless requests.
        // Stateful responses depend on previous conversation history,
        // so a simple prompt-only cache could return an incorrect answer.
		if (stateless) {
			String cached = cache.get(cacheKey);
			if (cached != null) {
				long ms = System.currentTimeMillis() - start;
				
                // Record cache-hit usage.
                // No LLM invocation occurred, so token usage is zero.
				usage.record(new TokenUsageService.Event(requestId, Instant.now(), client.clientName(), model, null, 0,
						0, 0, ms, true, "SUCCESS", 0, false, null));
				return ResponseEntity.ok(new ChatResponse(requestId, cached, true, model, null, 0, 0, 0, false, ms));
			}
		}
		try {
			
            // Step 9: Invoke the LLM through SupportAiService.
            // If sessionId is provided, Spring AI ChatMemory can retrieve
            // previous messages for that conversation and include them
            // in the LLM prompt, enabling stateful conversation.
            // If sessionId is absent, this is a stateless request.
			var result = ai.reply(safe, req.sessionId());
			
			 // Step 10: Extract token usage metadata from the LLM response.
			var counts = reader.read(result.response());
			long ms = System.currentTimeMillis() - start;
			
            // Step 11: Cache the response only for stateless requests.
            // Stateful responses are not cached because their answers
            // depend on the conversation history.
			if (stateless)
				cache.put(cacheKey, result.answer());
			
			// Step 12: Calculate the estimated LLM execution cost.
			double cost = (counts.prompt() * props.usage().inputCostPerMillion()
					+ counts.completion() * props.usage().outputCostPerMillion()) / 1_000_000d;
			
		     // Step 13: Record token usage, estimated cost and execution time.
			usage.record(new TokenUsageService.Event(requestId, Instant.now(), client.clientName(), model,
					req.sessionId(), counts.prompt(), counts.completion(), counts.total(), ms, false, "SUCCESS", cost,
					counts.available(), null));
			
		      // Step 14: Return the LLM response and usage information.
			return ResponseEntity.ok(new ChatResponse(requestId, result.answer(), false, model, req.sessionId(),
					counts.prompt(), counts.completion(), counts.total(), counts.available(), ms));
		} catch (RuntimeException ex) {
			
            // If LLM invocation or response processing fails,
            // record the failed request for monitoring and auditing.
			long ms = System.currentTimeMillis() - start;
			usage.record(new TokenUsageService.Event(requestId, Instant.now(), client.clientName(), model,
					req.sessionId(), 0, 0, 0, ms, false, "ERROR", 0, false, null));
			throw ex;
		}
	}

	@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<?> stream(@RequestHeader(value = "X-API-Key", required = false) String key,
			@Valid @RequestBody ChatRequest req) {
		var c = client(key);
		if (c == null)
			return ResponseEntity.status(401).body(Map.of("error", "invalid_api_key"));
		if (!limiter.tryConsume(key, c.requestsPerMinute()))
			return ResponseEntity.status(429).body(Map.of("error", "rate_limit_exceeded"));
		if (req.message().length() > props.gateway().maxPromptChars())
			return ResponseEntity.badRequest().body(Map.of("error", "prompt_too_large"));
		try {
			return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
					.body(ai.stream(guard.sanitize(req.message())));
		} catch (InputGuardService.UnsafePromptException e) {
			return ResponseEntity.badRequest().body(Map.of("error", "unsafe_prompt"));
		}
	}

	@PostMapping("/tickets/classify")
	public ResponseEntity<?> classify(@RequestHeader(value = "X-API-Key", required = false) String key,
			@Valid @RequestBody ClassifyRequest req) {
		var c = client(key);
		if (c == null)
			return ResponseEntity.status(401).body(Map.of("error", "invalid_api_key"));
		if (!limiter.tryConsume(key, c.requestsPerMinute()))
			return ResponseEntity.status(429).body(Map.of("error", "rate_limit_exceeded"));
		try {
			var result = ai.classify(guard.sanitize(req.message()));
			return ResponseEntity.ok(result);
		} catch (InputGuardService.UnsafePromptException e) {
			return ResponseEntity.badRequest().body(Map.of("error", "unsafe_prompt"));
		}
	}

	private GatewayProperties.Client client(String key) {
		return key == null ? null : props.gateway().apiKeys().get(key);
	}
}
