package com.acmesupport.ai.web;

import com.acmesupport.ai.service.PromptCacheService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cache")
public class CacheController {
	private final PromptCacheService cache;

	public CacheController(PromptCacheService c) {
		cache = c;
	}

	@GetMapping("/stats")
	public PromptCacheService.Stats stats() {
		return cache.stats();
	}
}
