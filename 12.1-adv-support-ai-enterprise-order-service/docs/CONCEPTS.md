# Concepts and Design Notes

## 1. Why this architecture?

Customer support combines different kinds of decisions. A single LLM should not own every decision. This design assigns each responsibility to the mechanism best suited to it:

| Need | Mechanism | Why |
|---|---|---|
| Coordinate a multi-service business transaction | Saga | Local transactions plus explicit compensations |
| Retrieve company policies and procedures | RAG | Ground answers in maintained knowledge |
| Invoke operational capabilities | MCP | Standard tool discovery and invocation protocol |
| Predict ticket urgency from labelled examples | Supervised ML | Statistical classification from historical outcomes |
| Enforce deterministic business policy | Drools | Explainable, testable rules |
| Plan and sequence investigation steps | Agentic LLM | Flexible goal-oriented orchestration within guardrails |

## 2. Saga pattern

A Saga is a sequence of local transactions. Each service commits its own data. If a later step fails, the coordinator invokes compensating business actions for earlier completed steps.

**Forward path**
1. Order service creates a Saga ID.
2. Drools evaluates whether the amount can proceed or needs manual review.
3. Inventory service reserves stock.
4. Payment service authorizes payment.
5. Order is marked complete.

**Compensation path**
- If payment authorization fails after reservation, call inventory release.
- If a later post-authorization step fails, void/refund payment and release inventory, in reverse order.

Compensation is not a database rollback. A refund is a new business operation, may fail, and may require retry/manual repair. A production Saga should persist state transitions, use idempotent commands, store retry counts, and publish events through an outbox.

### Choreography vs orchestration

* Choreography: services react to domain events; fewer central dependencies, but end-to-end flow is harder to visualize.
* Orchestration: a coordinator explicitly directs each step and compensation. This sample uses orchestration because it makes the learning flow visible.

### Failure matrix

| Failure point | Required action |
|---|---|
| Inventory reservation fails | Stop; nothing to compensate |
| Payment authorization fails | Release inventory |
| Post-payment confirmation fails | Refund/void payment, then release inventory |
| Compensation fails | Persist failure, retry safely, alert operations |

## 3. RAG (Retrieval-Augmented Generation)

RAG combines retrieval with generation. Documents are split/embedded/indexed in a vector store. At question time, the system embeds the query, retrieves semantically similar chunks, and supplies those chunks to the LLM as context.

**In this sample**: `/api/knowledge/ingest` inserts text into SimpleVectorStore (in-memory); `/api/knowledge/ask` uses `QuestionAnswerAdvisor` to retrieve context before generating an answer.

**RAG is not** fine-tuning, guaranteed truth, or a replacement for authorization. Retrieval quality depends on chunking, embeddings, metadata, freshness and access controls. Production responses should expose source references and abstain when evidence is insufficient.

## 4. MCP (Model Context Protocol)

MCP standardizes the interface between an AI host/client and external tool/resource servers. The MCP server exposes named tools and JSON-schema-described inputs. The client discovers and invokes them through the protocol.

**Sample tools**: `lookupTicket`, `escalateTicket`, `serviceStatus`.

MCP does not itself provide planning, memory, authorization or business correctness. The server-side implementation must authenticate callers, validate arguments, enforce authorization and audit side effects. Treat tool output as untrusted data, not instructions.

## 5. Supervised machine learning

Supervised learning trains a model from labelled examples `(features, target)`. Here, ticket text is transformed into TF-IDF numeric features and LogisticRegression learns to predict `LOW`, `MEDIUM`, `HIGH` or `CRITICAL`.

**Lifecycle**: collect labelled data -> clean/de-identify -> split train/validation/test -> train -> evaluate -> calibrate -> version -> deploy -> monitor -> retrain under governance.

Useful measures: confusion matrix, precision, recall, macro-F1, per-class recall, calibration error and drift. A high accuracy score can hide poor recall for rare critical incidents. The included dataset is intentionally tiny and only demonstrates the pipeline.

Temperature, top-p and top-k influence token sampling in generative models. They do not tune LogisticRegression or RandomForest predictions.

## 6. Drools rule engine

Drools evaluates facts against declarative rules. The sample applies a deterministic high-value review threshold before the Saga reserves inventory. Rules should encode explicit, approved business policy, with tests, versioning and decision audit records.

A useful separation is:
- ML: estimate likelihood/priority from patterns.
- Drools: apply explicit policy constraints.
- LLM: interpret language, summarize evidence and plan safe next steps.

Do not allow an LLM response to silently override a mandatory Drools rule.

## 7. LLM and agentic behavior

An LLM generates responses from prompts and context. Tool calling lets it request that an application execute a function. An agentic workflow adds a goal, state, tool selection, observation and iterative planning.

Typical bounded loop:
1. Receive a goal and context.
2. Ask the model to decide the next step.
3. If it requests a tool, validate permissions and execute it.
4. Return tool output to the model.
5. Repeat until the goal is complete, a limit is reached, or human approval is required.
6. Return a traceable summary.

Spring AI manages the model/tool interaction when tool callbacks are supplied. The agent endpoint in this project gives the model a goal and MCP tool callbacks. It is not a separate autonomous daemon. For reliable production agents, add explicit maximum tool rounds, deadlines, budgets, durable state, human-in-the-loop gates and a structured execution trace.

## 8. How the pieces cooperate in a real ticket flow

1. API receives a ticket.
2. Supervised ML predicts a priority and confidence.
3. Drools applies contractual SLA and escalation rules.
4. RAG retrieves relevant troubleshooting and policy content.
5. Agent uses MCP to inspect ticket metadata and service health.
6. Agent proposes a response or a permitted action.
7. If an action spans services (for example replacement order + inventory + payment adjustment), a Saga coordinates it.
8. Human approval is required for high-impact actions such as refunds or account closure.

## 9. Security and governance guardrails

- Least-privilege tool identities; separate read and write tools.
- Human approval for money movement, account changes and destructive actions.
- Prompt injection defenses: retrieved pages and tool results are data, never system instructions.
- PII minimization/redaction and tenant-aware retrieval filters.
- Strict JSON/schema validation; never execute arbitrary model-generated code or SQL.
- Rate limits, timeouts, max tool iterations, cost budgets and circuit breakers.
- Audit trail with user, model, prompt version, retrieved document IDs, tool arguments/results, policy version and Saga ID.
- Offline evaluation and red-team tests before model/prompt/rule rollout.

## 10. Suggested evolution to production

1. Replace in-memory service state with PostgreSQL per service.
2. Add Kafka and transactional outbox/inbox for asynchronous Saga events.
3. Persist Saga state machine and implement idempotent compensation retries.
4. Add Spring Security OAuth2 resource servers and service-to-service identity.
5. Add schema registry and versioned domain events.
6. Add RAG ingestion pipeline for PDFs, HTML and knowledge-base sources; chunk, deduplicate and attach ACL metadata.
7. Add model registry and reproducible ML training pipeline.
8. Add observability, dashboards, alerting and dead-letter queues.
9. Add evaluation datasets for RAG relevance, answer faithfulness, tool correctness and agent task success.
10. Establish human approval policies and operational runbooks.


## 11. No-Docker, in-memory deployment model

This variant intentionally has no Docker, compose, external database, cache or broker. Each microservice is a separate local JVM process and owns its own heap-resident demo state. HTTP calls cross process boundaries; Java collections are not shared between services. The RAG service uses Spring AI `SimpleVectorStore`, whose vector index lives in the AI service heap and disappears when that process stops. Ollama is installed natively on Windows and serves both chat (`llama3.2`) and embeddings (`nomic-embed-text`). The supervised classifier runs as a local Python/Uvicorn process and trains its tiny demonstration dataset in memory.

**Trade-off:** this is ideal for workshops, demos and understanding service boundaries, but not restart-safe. Re-ingest RAG documents after restarting AI service. Restarting order, inventory, payment or MCP services clears their local demo state. A production deployment needs durable persistence and reliable messaging; those are deliberately excluded here.


# Kafka Saga Choreography (project implementation)

Unlike orchestration, choreography has no central component directing every participant. Each microservice owns its local transaction and reacts to an event. The Order service applies Drools before publishing `order.created`. Inventory reserves stock and emits `inventory.reserved`; Payment authorizes and emits `payment.authorized` or `payment.failed`. On failure, Order emits `order.cancelled`; Inventory compensates by releasing stock and emits `inventory.released`.

**Why Kafka?** It decouples participants, buffers events while consumers are temporarily unavailable, and allows independent scaling. Events are keyed by `sagaId`. A Saga status endpoint provides a read model for this demo.

**Choreography vs orchestration:** Orchestration centralizes the process state machine and commands participants. Choreography distributes progression across event handlers. Choreography reduces central coordination but makes end-to-end tracing, cyclic flows, timeouts and failure recovery more complex.

**Important no-Docker clarification:** A real Kafka choreography across separate Spring Boot processes still requires a broker process. This project uses native Apache Kafka in KRaft mode on Windows, not Docker. Application domain state remains in-memory; Kafka broker records are stored in its local log directory. An embedded Kafka broker is useful for tests, but cannot be shared by separately launched services without changing the topology.

**Production safeguards:** use an eventId, idempotent handlers, retry/backoff, dead-letter topics, schema registry or versioned event contracts, correlation IDs, timeout/reconciliation jobs, transactional outbox, durable Saga state and metrics/tracing. The sample is intentionally simplified and does not promise exactly-once business processing.
