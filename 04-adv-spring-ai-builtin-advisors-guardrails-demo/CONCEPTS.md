# CONCEPTS — Spring AI Advisors and Guardrails

This document explains the purpose and boundaries of every advisor and guardrail in the demo.

## 1. What is a Spring AI Advisor?

An Advisor intercepts a `ChatClient` interaction. Depending on its implementation, it can:
- Inspect the prompt before the model is called.
- Modify the prompt.
- Stop the request by throwing an exception or returning a controlled result.
- Inspect the model response after the downstream chain returns.
- Observe each iteration in a tool-calling flow.

Spring AI 2.0 uses `CallAdvisor` for synchronous calls and `StreamAdvisor` for reactive streaming calls. The example uses synchronous `.call()`, so the custom input advisor implements `CallAdvisor`.

Advisor ordering matters:
- Lower order values execute earlier on the request path.
- Responses travel back through the advisor chain in reverse order.
- Tool calling is managed by Spring AI's tool-calling advisor when tools are registered with the ChatClient.

Official reference: https://docs.spring.io/spring-ai/reference/api/advisors.html

## 2. SimpleLoggerAdvisor

**Class:** `org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor`

Purpose:
- Logs the request and response passing through the advisor chain.
- Helps developers understand prompts and model outputs during development.
- Does not block malicious requests or sanitize responses.

Security classification: observability only.

Important risk:
- Raw prompts and responses can contain email addresses, phone numbers, account details, tokens, or confidential business information.
- Logging all content can create a second sensitive-data store.
- In production, prefer metadata-only logging, redaction before logging, access controls, retention limits, and secure log storage.

In this demo it is included to make the advisor chain visible during learning.

## 3. SafeGuardAdvisor

**Class:** `org.springframework.ai.chat.client.advisor.SafeGuardAdvisor`

Purpose:
- Blocks a model call when the input contains one of the configured sensitive words.
- Performs a simple case-insensitive check.
- Can return a configured failure response.

In this project it is configured with phrases such as:
- `reveal the system prompt`
- `ignore all previous instructions`

Limitations:
- It is not a semantic prompt-injection classifier.
- It does not reliably detect paraphrases, encoded instructions, homoglyphs, zero-width characters, or multi-step attacks.
- It does not authorize access to customer records.
- It does not inspect the tool's business permissions.

Treat it as a basic content filter, not a complete security boundary.

## 4. Custom InputGuardrailAdvisor

**Class:** `com.example.support.guardrail.InputGuardrailAdvisor`

This is a project-specific advisor implementing Spring AI's `CallAdvisor`.

Request path:
1. Read the user message from `ChatClientRequest`.
2. Reject null or blank input.
3. Reject input longer than 2000 characters.
4. Search for a small list of known injection phrases.
5. If accepted, call `chain.nextCall(request)` to continue to the next advisor.

The call to `chain.nextCall()` is important. Without it, the model is never called.

Why implement it as an advisor?
- The check is reusable across ChatClient calls using this configured client.
- It keeps input policy separate from controller logic.
- It can be unit tested independently.

Why is it not sufficient by itself?
- Substring matching is easy to bypass.
- The advisor only sees the prompt text; it does not automatically know the authenticated user's permissions.
- For authorization, use trusted server-side identity and policy checks.

## 5. OutputGuardrail

**Class:** `com.example.support.guardrail.OutputGuardrail`

This demo implements output validation in two parts: `OutputGuardrail` contains deterministic sanitization rules, and `OutputGuardrailAdvisor` invokes those rules inside the Spring AI advisor chain after the downstream model/tool-calling chain returns.

Checks:
1. Replace email-like values with `[REDACTED_EMAIL]`.
2. Replace phone-like values with `[REDACTED_PHONE]`.
3. Reject a small list of unsupported operational claims, such as “refund has been issued”.
4. Truncate responses longer than 4000 characters.

Why split the implementation into a policy component and an advisor?
- `OutputGuardrail` contains reusable, independently testable policy logic.
- `OutputGuardrailAdvisor` integrates that policy into the response path of Spring AI's `CallAdvisor` chain.
- The controller no longer needs to remember to call the sanitizer after every model invocation.

The advisor calls `chain.nextCall(request)` first. When the response returns, it inspects each generated assistant message. It skips tool-call messages so it does not rewrite tool arguments. For ordinary assistant text, it sanitizes the text and rebuilds the `Generation` and `ChatResponse`, preserving generation metadata, model metadata, token usage, and the ChatClient advisor context.

The advisor is ordered with `Ordered.LOWEST_PRECEDENCE`, so it is last on the request path and first on the response path. This lets it inspect the model output before the response is returned to the caller.

This implementation is for synchronous `CallAdvisor` calls. It does not intercept `.stream()` calls. A streaming application needs a `StreamAdvisor` that buffers/aggregates content before releasing it, or a different safe streaming design.

Limitations:
- Regex masking can produce false positives and false negatives.
- A phrase blocklist is not a factuality checker.
- It does not prove that a statement came from a tool result.
- It cannot detect every sensitive-data format.

## 6. StructuredOutputValidationAdvisor

Spring AI 2.0 includes `StructuredOutputValidationAdvisor`. In this project, the `/api/support/structured-summary` endpoint demonstrates it through:

```java
.entity(SupportSummary.class, spec -> spec.validateSchema());
```

Spring AI derives a JSON schema from the Java record. If the model response does not conform, the advisor can retry with validation feedback, up to its configured/default attempt limit.

What it validates:
- Required output structure and field types represented by the schema.
- JSON schema conformance of the generated result.

What it does not validate:
- Whether the facts are true.
- Whether the model disclosed PII or secrets.
- Whether a customer is authorized to see an order.
- Whether the response is free from prompt injection or harmful instructions.

Use it for output shape, not as a replacement for content or security guardrails. It is a non-streaming structured-output feature.

## 7. Tool execution guardrail

**Classes:** `OrderTools` and `PaymentTools`

The tools validate the order ID using the format `O` followed by four digits.

The controller also validates the path variable before calling the LLM. This is deliberate defense in depth:
- The REST boundary rejects malformed order IDs before spending tokens.
- The tool boundary validates arguments even if the tool is invoked from another controller or agent.

Production authorization must go further:
- Obtain the authenticated customer identity from the server's security context.
- Verify that the order belongs to that customer.
- Scope payment lookups by customer/tenant.
- Never accept a model-provided customer identity as proof of authorization.

A valid-looking order ID is not the same as an authorized order ID.

## 8. Tool calling and agentic behaviour

The `@Tool` methods expose Java methods as tools to the model.

The configured ChatClient uses:

```java
.defaultTools(paymentTools, orderTools)
```

Spring AI supplies the tool definitions to the model. The model may request tool execution. Spring AI invokes the corresponding Java method, returns the tool result to the model, and continues the tool-calling loop until a final response is produced.

The system prompt requests both tools, but prompts are not enforcement mechanisms. If the business requires both checks on every request, invoke both services directly in application code and pass their trusted results to the LLM for explanation.

## 9. Why the example does not stream

The controller uses `.call().content()`. The custom `OutputGuardrailAdvisor` inspects and sanitizes the completed response inside the advisor chain before `.content()` returns to the controller.

If you stream model tokens directly to a browser, a sensitive phone number or unsafe claim may already have been sent before a post-processing check runs. For strict output policies:
- Buffer the response and validate it before sending, or
- Use a streaming guardrail that safely withholds data until validation is possible.

## 10. Advisor vs guardrail vs business authorization

| Mechanism | What it does | What it does not do |
|---|---|---|
| SimpleLoggerAdvisor | Observes/logs interactions | Does not secure the request |
| SafeGuardAdvisor | Basic sensitive-word filtering | Does not detect every injection |
| InputGuardrailAdvisor | Validates prompt text | Does not establish user identity |
| OutputGuardrailAdvisor + OutputGuardrail | Masks/rejects selected output patterns on the response path | Does not prove factuality |
| StructuredOutputValidationAdvisor | Validates structured output schema and retries | Does not detect PII or prove facts |
| Tool validation | Validates tool arguments | Does not by itself prove record ownership |
| Authorization service | Enforces user/tenant permissions | Must be implemented against trusted identity |

## 11. Suggested production evolution

1. Add Spring Security authentication.
2. Derive customer and tenant IDs from the authenticated principal.
3. Authorize every order/payment tool call against the customer and tenant.
4. Replace substring injection detection with layered policy checks and adversarial tests.
5. Add structured output schemas for machine-consumed responses.
6. Add an audit event for every tool invocation and policy decision.
7. Use rate limits, timeouts, retries, circuit breakers, and bounded tool permissions.
8. Add human approval before irreversible actions such as refunds.
9. Keep PII out of logs and metrics.
10. Test prompt injection, data exfiltration, tool misuse, and output leakage as security tests.

## 12. Key takeaway

Advisors are an interception and composition mechanism. Guardrails are policy checks. Authorization is a separate server-side security responsibility.

Do not rely on a system prompt, a single advisor, or the LLM itself to enforce critical business rules.
