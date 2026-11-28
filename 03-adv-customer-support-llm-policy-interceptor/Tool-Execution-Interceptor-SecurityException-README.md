# Tool Execution Policy Rejection and REST Exception Handling

## What happens

The LLM can request a tool, but it cannot execute the Java method directly.
Spring AI routes the request through `GuardedToolCallback`.

1. `GuardedToolCallback.call(...)` receives the tool name and JSON arguments.
2. It calls `ToolExecutionPolicy.validate(...)`.
3. The policy checks that the tool is allowed and that `orderId` matches `ORD-[0-9]{3,}`.
4. If validation fails, the policy throws `java.lang.SecurityException`.
5. The exception propagates through Spring AI's tool-calling pipeline.
6. `GlobalExceptionHandler`, registered with `@RestControllerAdvice`, inspects the exception and its nested causes.
7. If a `SecurityException` is found, the API returns HTTP 403 with error code `TOOL_EXECUTION_REJECTED`.

## Why inspect nested causes?

Frameworks may wrap an exception before it reaches the controller. Handling only a direct `SecurityException` can therefore miss a policy rejection. The updated handler walks the cause chain and finds the original security exception.

## Expected policy rejection response

```json
{
  "timestamp": "2026-11-02T05:32:52.332Z",
  "status": 403,
  "error": "TOOL_EXECUTION_REJECTED",
  "message": "The requested tool operation was blocked by the security policy: Invalid order ID",
  "path": "/api/support/INVALID"
}
```

The timestamp is generated for each response. The response intentionally excludes the Java stack trace.

## Important distinction

`@RestControllerAdvice` formats exceptions that escape the REST controller. It does not catch exceptions inside a tool callback and turn them into an LLM tool result. In this demo, a policy violation ends the HTTP request with 403. If the desired behavior is for the LLM to continue and explain the rejection, catch the policy exception inside `GuardedToolCallback` and return a controlled tool-result string instead.

## Security note

This demo validates tool arguments, but production systems must also authenticate the caller and verify that the caller is authorized to access the requested order. Validate the order ID at the REST boundary too; do not rely on the model to pass it unchanged.
