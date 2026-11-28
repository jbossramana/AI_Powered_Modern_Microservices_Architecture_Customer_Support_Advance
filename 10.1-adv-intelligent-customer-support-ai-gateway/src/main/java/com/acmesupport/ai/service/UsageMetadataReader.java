package com.acmesupport.ai.service;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;

@Component
public class UsageMetadataReader {
	public record Counts(long prompt, long completion, long total, boolean available) {
	}

	public Counts read(ChatResponse response) {
		try {
			Object usage = response.getMetadata().getUsage();
			if (usage == null)
				return new Counts(0, 0, 0, false);
			long p = number(usage, "getPromptTokens", "promptTokens");
			long c = number(usage, "getCompletionTokens", "completionTokens");
			long t = number(usage, "getTotalTokens", "totalTokens");
			if (t == 0)
				t = p + c;
			return new Counts(p, c, t, p + c + t > 0);
		} catch (Exception e) {
			return new Counts(0, 0, 0, false);
		}
	}

	private long number(Object target, String... names) {
		for (String name : names)
			try {
				Method m = target.getClass().getMethod(name);
				Object v = m.invoke(target);
				if (v instanceof Number n)
					return n.longValue();
			} catch (Exception ignored) {
			}
		return 0;
	}
}
