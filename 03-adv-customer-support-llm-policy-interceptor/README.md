# Customer Support Agent — Guarded Spring AI Tool Calls

Demo project using Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, Maven, and Ollama.

## What it demonstrates

- The LLM requests `checkPaymentStatus` and `checkOrderStatus`.
- `GuardedToolCallback` validates tool names and JSON arguments before delegating.
- `ToolExecutionPolicy` rejects malformed or unauthorized order IDs.
- `GlobalExceptionHandler` converts direct or wrapped `SecurityException` instances into a safe HTTP 403 response.
- The LLM receives successful tool results and creates the final answer.
- Demo data is in-memory; this is not a production payment integration.

## Requirements

- JDK 21
- Maven 3.9+
- Ollama installed and running
- Windows CMD for the curl examples below

> Version note: verify that Spring Boot `4.1.0` and Spring AI `2.0.0` are available in your Maven repositories.

## 1. Start Ollama (Windows CMD)

```cmd
ollama pull llama3.2:latest
ollama serve
```

If Ollama is already running as a Windows service/application, do not start a second server. Verify it:

```cmd
curl.exe http://localhost:11434/api/tags
```

The response should list `llama3.2:latest`. The default model is configured in `application.properties`.

## 2. Start Spring Boot

Open another CMD window in the folder containing `pom.xml`:

```cmd
java -version
mvn -version
mvn clean spring-boot:run
```

Wait for the application to start on port `8080`.

## 3. Test valid order investigation

The demo order `ORD-101` has payment `SUCCESS` and order `NOT_CREATED`.

```cmd
curl.exe -i -X POST "http://localhost:8080/api/support/ORD-101" -H "Content-Type: application/json" -d "{\"question\":\"Payment was deducted but the order was not created. Investigate the issue.\"}"
```

Expected behavior:
- Spring AI asks the model to call both tools with the exact order ID from the URL.
- `ToolExecutionPolicy` validates each tool name and its `orderId`.
- The guarded callback delegates only when validation passes.
- The model summarizes the actual tool results.

Tool selection and ordering are model-driven; the exact wording can vary.

## 4. Test invalid order ID — policy rejection

This intentionally uses an invalid order ID. When the model requests a tool with `INVALID`, `ToolExecutionPolicy` throws `SecurityException`. `GlobalExceptionHandler` inspects the exception cause chain and returns HTTP `403 Forbidden`, rather than exposing a stack trace.

```cmd
curl.exe -i -X POST "http://localhost:8080/api/support/INVALID" -H "Content-Type: application/json" -d "{\"question\":\"Check payment status and order status for this order.\"}"
```

Expected response shape:

```json
{
  "timestamp": "2026-11-02T05:32:52.332Z",
  "status": 403,
  "error": "TOOL_EXECUTION_REJECTED",
  "message": "The requested tool operation was blocked by the security policy: Invalid order ID",
  "path": "/api/support/INVALID"
}
```

The timestamp will differ. The HTTP status and error code are the important parts.

> This test relies on the model requesting a tool. If the model does not call a tool, the tool policy will not run. For deterministic API validation, validate the path variable before calling the model as an additional layer.

## 5. More Windows CMD curl tests

### A. Another valid order ID

```cmd
curl.exe -i -X POST "http://localhost:8080/api/support/ORD-102" -H "Content-Type: application/json" -d "{\"question\":\"Please check payment and order status.\"}"
```

### B. Invalid order ID with a payment question

```cmd
curl.exe -i -X POST "http://localhost:8080/api/support/ABC-12" -H "Content-Type: application/json" -d "{\"question\":\"Was my payment successful?\"}"
```

Expected: HTTP 403 with `TOOL_EXECUTION_REJECTED` if the model calls the guarded tool.

### C. Missing request body

```cmd
curl.exe -i -X POST "http://localhost:8080/api/support/ORD-101" -H "Content-Type: application/json"
```

Expected: HTTP 400 with `INVALID_REQUEST_BODY` (depending on how the HTTP client sends an empty body).

### D. Malformed JSON

```cmd
curl.exe -i -X POST "http://localhost:8080/api/support/ORD-101" -H "Content-Type: application/json" -d "{\"question\":"
```

Expected: HTTP 400 with `INVALID_REQUEST_BODY`.

### E. Unknown URL

```cmd
curl.exe -i -X POST "http://localhost:8080/api/support/ORD-101/unknown" -H "Content-Type: application/json" -d "{\"question\":\"Check status.\"}"
```

Expected: HTTP 404.

## 6. How exception handling works

1. The model requests a tool.
2. Spring AI invokes `GuardedToolCallback`.
3. `GuardedToolCallback` calls `ToolExecutionPolicy.validate(...)`.
4. The policy throws `SecurityException` when the tool name or arguments violate policy.
5. The exception travels out through the Spring AI tool-calling pipeline.
6. `GlobalExceptionHandler` searches the exception and its nested causes for `SecurityException`.
7. The REST API returns HTTP 403 and a structured JSON error.

The handler also converts malformed JSON to HTTP 400 and unexpected errors to a generic HTTP 500 response. It never returns the Java stack trace to the caller.

## 7. Source code map

- `controller/SupportController.java` — REST endpoint and prompt.
- `security/ToolExecutionPolicy.java` — tool allowlist and argument validation.
- `security/GuardedToolCallback.java` — validates before invoking the real tool.
- `security/GlobalExceptionHandler.java` — consistent REST error responses.
- `tools/PaymentTools.java` — demo payment status tool.
- `tools/OrderTools.java` — demo order status tool.
- `config/AiConfig.java` — registers only guarded tool callbacks.
- `src/main/resources/application.properties` — Ollama and server configuration.

## 8. Important security notes

- This is an educational demonstration, not a complete authorization solution.
- Validate the authenticated user's access to the order against a real database.
- Validate the URL order ID at the REST boundary as well as validating tool arguments.
- Do not treat model output or a user-supplied order ID as proof of authorization.
- Avoid exposing unguarded callbacks for the same tools through another execution path.
- Add authentication, authorization, rate limits, audit logs, argument-size limits, and security tests before production use.
