# LangGraph4j Customer Support Agent

A learning project demonstrating **Spring Boot + Spring AI + Ollama + LangGraph4j + RAG + MCP + human approval**. The graph topology is declared in XML and loaded into a LangGraph4j `StateGraph` at application startup.

## XML-driven workflow orchestration with LangGraph4j, Spring AI, RAG, MCP tools, and a human-approval stage.

The key idea is:
- XML defines the workflow sequence.
- XmlWorkflowLoader reads XML and dynamically creates the LangGraph.
- GraphNodeCatalog provides the actual business logic for each node.
- SupportState carries information from one node to the next.
- LangGraph executes the nodes in the defined order.

## Versions

- JDK 21
- Spring Boot 4.1.0
- Spring AI 2.0.2
- LangGraph4j 1.9.7
- Ollama chat model: `llama3.2`
- Ollama embedding model: `nomic-embed-text`

## Important corrections in this revision

- `Channels.base()` is not a no-argument method in LangGraph4j 1.9.7. Every state field uses `Channels.base(() -> "")` (or a meaningful String default).
- The MCP SDK client type is not `org.springframework.ai.mcp.client.McpSyncClient`. This project avoids that incorrect import and injects Spring AI's `org.springframework.ai.mcp.SyncMcpToolCallbackProvider` instead.
- There is no `spring-ai-starter-vector-store-simple` dependency. `SimpleVectorStore` is provided by `org.springframework.ai:spring-ai-vector-store`.
- The controller uses LangGraph4j 1.9.7 `invoke(initialState)` to obtain the final `SupportState`. That Map overload is deprecated in favour of the newer `GraphInput`/`RunnableConfig` API, but remains available in 1.9.7.

## What the example does

Customer `C1001` asks about a duplicate payment on order `O1001`.

1. **RAG node** retrieves refund and security policy snippets from an in-memory `SimpleVectorStore`.
2. **MCP node** exposes discovered MCP tools to the Llama 3.2 call. The model is instructed to call `get_order_status` on the separate MCP server.
3. **Decision node** classifies the request as `REFUND_REVIEW` or `INFORMATION_ONLY`.
4. **Approval node** marks refund cases as `PENDING`; information-only cases are marked `NOT_REQUIRED`.
5. **Response node** creates a provisional response.
6. The REST API returns a `runId` for refund review. A human reviewer submits APPROVED or REJECTED to the approval endpoint.

**Approval semantics:** this teaching version implements the human gate at the REST boundary after the graph has completed its analysis nodes. It does not use LangGraph4j checkpoint-based interrupt/resume. No refund API exists and no refund is executed by this sample. The in-memory pending-approval map is lost on restart.

## Project layout

```text
langgraph4j-support/
├── pom.xml
├── README.md
├── CONCEPTS.md
├── mcp-demo-server/
│   ├── pom.xml
│   └── src/main/java/com/example/mcpserver/
│       ├── McpDemoServer.java
│       └── OrderTools.java
└── src/main/
    ├── java/com/example/agent/
    │   ├── SupportAgentApplication.java
    │   ├── config/RagConfig.java
    │   ├── graph/
    │   │   ├── SupportState.java
    │   │   ├── GraphNodeCatalog.java
    │   │   └── XmlWorkflowLoader.java
    │   └── web/WorkflowController.java
    └── resources/
        ├── application.yml
        └── support-flow.xml
```

## Architecture

```mermaid
flowchart TD
  A[POST /api/support/process] --> B[XML workflow loader]
  B --> C[LangGraph4j StateGraph]
  C --> D[RAG: SimpleVectorStore]
  D --> E[MCP node: get_order_status]
  E --> F[Decision: Ollama Llama 3.2]
  F --> G[Approval gate node]
  G --> H[Provisional response]
  H --> I{Approval required?}
  I -->|No| J[Return completed response]
  I -->|Yes| K[Return runId: pending review]
  K --> L[POST /{runId}/approval]
  L --> M[Final reviewer decision]
```

## Prerequisites

1. JDK 21.
2. Maven 3.9 or later.
3. Ollama installed and running.
4. Pull the models:

```powershell
ollama pull llama3.2
ollama pull nomic-embed-text
ollama list
```

## Build the main project

From the project root:

```powershell
mvn -U clean package
```

The project is not a Maven multi-module build: the MCP server is a separate Maven application under `mcp-demo-server`.

## Start the MCP server

Open terminal 1:

```powershell
cd mcp-demo-server
mvn spring-boot:run
```

It listens on port `8091` and exposes the `get_order_status` MCP tool over Streamable HTTP. The demo data returns a delivered order with a duplicate-payment flag for `C1001` / `O1001`.

## Start the main application

Open terminal 2 from the project root:

```powershell
mvn spring-boot:run
```

The API listens on `http://localhost:8080`.

## Test using Windows curl.exe

Use `curl.exe` (not the PowerShell `curl` alias). The JSON quoting below is compatible with Windows PowerShell.

### 1. Start a duplicate-payment investigation

curl.exe -X POST "http://localhost:8080/api/support/process" -H "Content-Type: application/json" -d "{\"customerId\":\"C1001\",\"orderId\":\"O1001\",\"question\":\"I was charged twice for my order. Please investigate and tell me if I can get a refund.\"}"

Expected shape (the generated runId and retrieved text vary):

```json
{
  "runId": "<generated-run-id>",
  "status": "AWAITING_HUMAN_APPROVAL",
  "decision": "REFUND_REVIEW",
  "knowledge": "...retrieved refund policy...",
  "orderDetails": "...MCP get_order_status result...",
  "message": "Supervisor review is required. No refund has been executed."
}
```

Copy the `runId` from the actual response.

### 2. Approve the case

Replace `YOUR_RUN_ID` with the returned value:

curl.exe -X POST "http://localhost:8080/api/support/f78cd39a-f8da-4184-9a92-039f9d37198a/approval" -H "Content-Type: application/json" -d "{\"reviewer\":\"supervisor1\",\"decision\":\"APPROVED\",\"comment\":\"Duplicate payment evidence verified.\"}"

### 3. Reject another case

Start a second `/process` request to obtain a new runId, then use:

curl.exe -X POST "http://localhost:8080/api/support/f78cd39a-f8da-4184-9a92-039f9d37198a/approval" -H "Content-Type: application/json" -d "{\"reviewer\":\"supervisor1\",\"decision\":\"REJECTED\",\"comment\":\"Payment evidence is insufficient.\"}"

### 4. Information-only request

curl.exe -X POST "http://localhost:8080/api/support/process" -H "Content-Type: application/json" -d "{\"customerId\":\"C1001\",\"orderId\":\"O1001\",\"question\":\"What is the current delivery status of my order?\"}"


If the model classifies it as `INFORMATION_ONLY`, the API returns `COMPLETED` with `approvalStatus` set to `NOT_REQUIRED`.

## Troubleshooting

- **`Channels.base()` compile error:** use `Channels.base(() -> "")`. In LangGraph4j 1.9.7 the method requires a `Supplier<T>`.
- **MCP client import unresolved:** do not import `org.springframework.ai.mcp.client.McpSyncClient`. This project uses `org.springframework.ai.mcp.SyncMcpToolCallbackProvider`, which is Spring AI's callback-provider abstraction.
- **Vector-store starter unresolved:** use `spring-ai-vector-store`; do not use `spring-ai-starter-vector-store-simple`.
- **MCP call fails:** start the `mcp-demo-server` first and verify port 8091 is reachable. Check the client connection URL in `src/main/resources/application.yml`.
- **Ollama model error:** run `ollama list`; pull both `llama3.2` and `nomic-embed-text`.
- **Embedding error:** check Ollama is running at `http://localhost:11434` and the embedding model name is correct.
- **XML loader error:** node `bean` attributes must match zero-argument methods in `GraphNodeCatalog`.

## Limitations and production hardening

This is a compact learning example. The pending approvals are in-memory, reviewer identity is supplied by the request (not authenticated), and the demo does not execute a refund. For production, use durable LangGraph4j checkpointing or a database-backed workflow store, authenticated role-based reviewer authorization, idempotent decisions, audit logging, tool allow-lists, MCP authentication, timeouts, bounded retries, prompt-injection defenses, PII redaction, and integration tests.
