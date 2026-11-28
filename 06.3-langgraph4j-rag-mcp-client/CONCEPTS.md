# Concepts — Spring AI + LangGraph4j Customer Support Agent

## 1. LangGraph4j: graph orchestration

LangGraph4j represents an application workflow as a graph. A **node** performs one step; an **edge** specifies which node runs next. The graph passes a shared `AgentState` between nodes. This separates orchestration from the implementation of each operation.

In this project, the nodes are RAG, MCP investigation, decision, approval-gate marking, and response composition.

## 2. XML workflow definition

`src/main/resources/support-flow.xml` declares node IDs, the corresponding zero-argument method names in `GraphNodeCatalog`, and the edges. `XmlWorkflowLoader` reads the XML and creates a LangGraph4j `StateGraph` using `addNode` and `addEdge`.

LangGraph4j's native API is Java-based; XML is an application-level configuration layer written for this example, not a built-in LangGraph4j XML feature. The loader disables DTDs and external entities to avoid XML external-entity processing.

## 3. Graph state and channels

`SupportState` extends `AgentState`. It stores customer/order identifiers, the customer question, retrieved policy text, MCP result, decision, approval status/comment, and response.

LangGraph4j 1.9.7 declares a channel using `Map<String, Channel<?>>`. `Channels.base` takes a `Supplier<T>` that supplies the default value. Therefore this project uses `Channels.base(() -> "")` for String fields. `Channels.base()` with no argument does not compile in this version.

A base channel replaces a previous value when a node emits a new value. An appender channel is appropriate when values such as messages should accumulate.

## 4. RAG — Retrieval-Augmented Generation

RAG combines retrieval from a trusted knowledge source with generation by an LLM.

1. `RagConfig` creates a `SimpleVectorStore` backed by the configured Ollama `EmbeddingModel`.
2. At startup it adds sample refund, order, and security policy documents.
3. The `ragNode` embeds the customer question and performs similarity search.
4. The retrieved text is stored in the graph state's `knowledge` field.
5. Later nodes receive that evidence as part of the state.

The project uses `spring-ai-vector-store` because that module contains the `SimpleVectorStore` implementation. There is no `spring-ai-starter-vector-store-simple` starter. The in-memory store is for learning/testing; production requires persistent storage, document versioning, access-control filters, and controlled ingestion.

## 5. Embeddings and vector similarity

An embedding model converts text into a vector of numbers. The vector store compares the query vector with document vectors and returns semantically similar documents. `nomic-embed-text` creates embeddings; `llama3.2` is the chat/reasoning model. They serve different purposes.

## 6. MCP — Model Context Protocol

The MCP server is a separate Spring Boot application in `mcp-demo-server`. Its `OrderTools` bean exposes `get_order_status` using `@McpTool`.

The main application uses `spring-ai-starter-mcp-client`. Spring AI discovers remote MCP tools and exposes them through `SyncMcpToolCallbackProvider`. `GraphNodeCatalog.mcpNode()` passes that provider to the `ChatClient` call using `.tools(mcpTools)`. Llama 3.2 is instructed to call the order lookup tool. The MCP client performs the protocol interaction with the server and the result is returned to the graph node.

Do not confuse these two types:

- `org.springframework.ai.mcp.SyncMcpToolCallbackProvider` is Spring AI's callback provider used by this project.
- `McpSyncClient` is part of the underlying MCP Java SDK (`io.modelcontextprotocol.client`), not `org.springframework.ai.mcp.client`.

## 7. Decision-making

The decision node asks Llama 3.2 to classify the request as `REFUND_REVIEW` or `INFORMATION_ONLY` using the retrieved policy and MCP evidence. Java normalizes the model response to a strict allow-list. Only an explicit `INFORMATION_ONLY` label bypasses review; unexpected or malformed model output falls back to `REFUND_REVIEW` (fail closed).

Do not let a free-form LLM response directly authorize a financial operation. Use deterministic policy rules for monetary thresholds, customer eligibility, and transaction verification.

## 8. Human approval gate

For `REFUND_REVIEW`, the graph's approval node sets `approvalStatus=PENDING`. The API returns a `runId` and stores the provisional state in a concurrent in-memory map. A reviewer then posts `APPROVED` or `REJECTED` to `/api/support/{runId}/approval`. The endpoint consumes the pending entry once and returns the final reviewer decision.

This version demonstrates a REST-mediated human approval gate after graph analysis. It is **not** a LangGraph4j checkpoint interrupt/resume implementation: the graph does not pause at a checkpoint and resume after the reviewer response. Pending data is lost on application restart. Production should use a persistent checkpoint saver or database-backed workflow state and authenticated reviewer identity/authorization.

No refund tool or refund API is included. Approval only records the human decision; it does not move money.

## 9. Why separate nodes?

| Node | Responsibility | State output |
|---|---|---|
| `retrieveKnowledge` | Retrieve relevant policy documents | `knowledge` |
| `inspectOrder` | Ask the model to invoke MCP order lookup | `orderDetails` |
| `makeDecision` | Classify the case | `decision` |
| `humanApproval` | Mark whether review is required | `approvalStatus`, `approvalComment` |
| `composeResponse` | Compose provisional response | `answer` |

The graph structure is intentionally linear for beginner clarity. A later iteration can use conditional edges so information-only requests branch directly to the response node and refund cases enter a checkpoint-backed approval interruption.

## 10. Safety and production hardening

- Treat retrieved documents and MCP tool output as untrusted input; they may contain prompt injection.
- Do not allow instructions inside retrieved content to override system policy.
- Allow-list MCP tools and validate tool arguments on the server.
- Never expose a refund execution tool to the LLM before an authenticated human approval.
- Authenticate the reviewer and authorize decisions by role, amount, and business policy.
- Persist graph state and approval events; enforce idempotency and expiration.
- Redact secrets and payment data from prompts and logs.
- Add bounded retries, timeouts, circuit breakers, observability, token/cost metrics, and integration tests.

## 11. Learning exercises

1. Add a deterministic `policyCheck` node for refund limits.
2. Add conditional edges to bypass approval for information-only questions.
3. Add a LangGraph4j checkpoint saver and implement actual interrupt/resume.
4. Replace `SimpleVectorStore` with PostgreSQL/pgvector.
5. Add a refund MCP tool that can only be called after approval.
