
package com.example.support.guardrail;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class OutputGuardrail {

    // Detect email addresses.
    private static final Pattern EMAIL =
            Pattern.compile(
                    "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"
            );

    // Detect common international and domestic phone number formats.
    private static final Pattern PHONE =
            Pattern.compile(
                    "(?<!\\d)(?:\\+?\\d[\\d ()-]{8,}\\d)(?!\\d)"
            );

    // Detect unsupported operational promises.
    private static final Pattern UNSUPPORTED_PROMISE =
            Pattern.compile(
                    "(?i)\\b(" +
                    "refund has been issued|" +
                    "refund is processed|" +
                    "account has been deleted|" +
                    "payment has been reversed|" +
                    "refund has been completed|" +
                    "refund has been processed" +
                    ")\\b"
            );

    // Maximum allowed assistant response length.
    private static final int MAX_RESPONSE_LENGTH = 4000;

    public String sanitize(String response) {

        // 1. Handle empty response.
        if (response == null || response.isBlank()) {

            return "I could not generate a response. Please try again.";
        }

        System.out.println(
                "OutputGuardrail: Original response length = "
                        + response.length()
        );

        // 2. Mask sensitive information first.
        String masked = EMAIL.matcher(response)
                .replaceAll("[REDACTED_EMAIL]");

        masked = PHONE.matcher(masked)
                .replaceAll("[REDACTED_PHONE]");

        // 3. Check for unsupported business promises.
        if (UNSUPPORTED_PROMISE.matcher(masked).find()) {

            throw new OutputRejectedException(
                    "Response rejected because it contains " +
                    "an unsupported operational promise."
            );
        }

        // 4. Truncate responses exceeding 4000 characters.
        if (masked.length() > MAX_RESPONSE_LENGTH) {

            System.out.println(
                    "OutputGuardrail: Response exceeds "
                            + MAX_RESPONSE_LENGTH
                            + " characters. Truncating."
            );

            masked = masked.substring(0, MAX_RESPONSE_LENGTH)
                    + "\n[Response truncated]";
        }

        // 5. Log final response length.
        System.out.println(
                "OutputGuardrail: Final response length = "
                        + masked.length()
        );

        return masked;
    }
}
