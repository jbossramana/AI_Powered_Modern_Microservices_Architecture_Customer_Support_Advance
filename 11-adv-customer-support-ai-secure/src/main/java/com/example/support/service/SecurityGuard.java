package com.example.support.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecurityGuard {
    private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]*?){13,19}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[ .()-]?){9,14}(?!\\d)");
    private static final Pattern SECRET = Pattern.compile("(?i)(api[_-]?key|password|secret|token)\\s*[:=]\\s*[^\\s,;]+|sk-[A-Za-z0-9_-]{12,}");
    private static final String[] INJECTION_MARKERS = {
			"ignore all previous instructions", "disregard previous instructions",
        "reveal your system prompt", "print the system prompt", "developer message is",
        "override your safety", "act as an unrestricted", "jailbreak",
        "show me all customers", "dump the database"
    };
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxChars;
    private final int requestsPerMinute;

    public SecurityGuard(@Value("${app.security.max-message-chars:2000}") int maxChars,
                         @Value("${app.security.requests-per-minute:20}") int requestsPerMinute) {
        this.maxChars = maxChars;
        this.requestsPerMinute = requestsPerMinute;
    }

    public String validateInput(String input, String actor) {
        if (input == null || input.isBlank()) throw new SecurityException("Message must not be empty.");
        if (input.length() > maxChars) throw new SecurityException("Message exceeds the configured character limit of " + maxChars + ".");
        String lower = input.toLowerCase();
        for (String marker : INJECTION_MARKERS) {
            if (lower.contains(marker)) throw new SecurityException("Potential prompt-injection attempt blocked by the input security layer.");
        }
        if (CARD.matcher(input).find()) throw new SecurityException("Payment-card-like data is not accepted. Do not submit card numbers or secrets.");
        return input.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
    }

    public void checkRate(String actor) {
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(actor, (key, old) -> old == null || now - old.start >= 60
            ? new Window(now, 1) : new Window(old.start, old.count + 1));
        if (window.count > requestsPerMinute) throw new RateLimitException("Rate limit exceeded. Try again in one minute.");
    }

    public String sanitizeOutput(String output, int maxOutputChars) {
        if (output == null || output.isBlank()) return "I could not generate a reliable answer. Please contact customer support.";
        String safe = SECRET.matcher(output).replaceAll("[REDACTED]");
        safe = EMAIL.matcher(safe).replaceAll("[REDACTED_EMAIL]");
        safe = PHONE.matcher(safe).replaceAll("[REDACTED_PHONE]");
        return safe.length() > maxOutputChars ? safe.substring(0, maxOutputChars) + "…" : safe;
    }

    public boolean looksLikeInjection(String text) {
        String lower = text.toLowerCase();
        for (String marker : INJECTION_MARKERS) if (lower.contains(marker)) return true;
        return false;
    }

    private record Window(long start, int count) { }
    public static class RateLimitException extends RuntimeException { public RateLimitException(String m) { super(m); } }
}
