# Concepts — Secure Customer Support AI

This document explains the architecture and security ideas independently of the source code.

## 1. Spring AI vs Ollama vs Llama 3.2

- **Ollama** runs models locally and exposes an HTTP API on `localhost:11434`.
- **Llama 3.2** is the chat model. It generates the natural-language answer.
- **nomic-embed-text** is an embedding model. It converts text into numeric vectors so semantically similar questions and FAQ passages can be matched.
- **Spring AI** is the Java integration layer. It provides `ChatClient`, `EmbeddingModel`, `Document`, `VectorStore` and the Ollama integration.
- **Spring Boot** hosts the REST API, dependency injection, configuration, security and database integration.

The chat model and embedding model perform different jobs. A chat model writes an answer; an embedding model represents meaning numerically for retrieval.

## 2. RAG (Retrieval-Augmented Generation)

RAG adds relevant trusted knowledge to a model request at runtime:

1. Curated FAQ documents are embedded and stored in the vector store.
2. A customer question is embedded using the same embedding model.
3. Similarity search finds the closest FAQ documents.
4. The application supplies only a small number of approved documents as context.
5. The LLM generates an answer grounded in that context.

RAG does not retrain Llama. It retrieves external knowledge and places selected text into the current prompt.

### Why embeddings are useful

A keyword search looks for matching words. Vector similarity can find semantically related language. For example, `When will my money come back?` may retrieve the refund FAQ even when the customer does not use the word `refund`.

## 3. Request flow and responsibilities

### Controller

`ChatController` is the HTTP adapter. It accepts JSON, reads the authenticated principal and delegates to `ChatService`. It should not contain prompt logic or vector search rules.

### Service / use case

`ChatService` coordinates the use case: validate the request, retrieve relevant knowledge, build the prompt, call Spring AI, sanitize the answer and write an audit event.

### Security guard

`SecurityGuard` is deterministic Java code. It applies limits and known-pattern checks before the LLM is called, and sanitizes the generated text afterwards. Security checks must not be left exclusively to a system prompt because the model can be manipulated.

### Knowledge base service

`KnowledgeBaseService` owns FAQ seeding, ingestion quarantine, approval and retrieval. It only returns documents marked as approved and public.

### Vector store

`SimpleVectorStore` stores document text, metadata and embeddings in memory. It is useful for learning and automated tests. It loses its data on restart and is not a production database.

### Audit service

`AuditService` writes security-relevant events to H2. The example intentionally avoids writing raw prompts and secrets to the audit table.

## 4. OWASP mapping used by this project

The OWASP GenAI Security Project published a 2026 LLM Top 10. The project maps the user's requested attack classes to that edition. Some wording differs from earlier lists, so the mapping is important.

| User's requested concern | OWASP 2026 mapping | What can go wrong | Demonstrated control |
|---|---|---|---|
| Prompt injection | LLM01 Prompt Injection | Malicious user or retrieved text attempts to change the assistant's goal | Input signature checks; strict system instructions; retrieved text treated as data |
| Sensitive information disclosure | LLM02 Sensitive Information Disclosure | Personal data, credentials or confidential records appear in model context or output | Reject card-like input; no private customer data in RAG; output redaction; least-privilege context |
| Excess data / context exposure | LLM08 Hidden Context Exposure (and overlaps LLM02) | More context, internal instructions or data is exposed than the task needs | Top-3 retrieval; context size cap; no prompt secrets; no customer data source |
| Data and model poisoning | LLM05 Data and Model Poisoning | Malicious documents are embedded and influence answers | Quarantine on ingestion; obvious injection scan; explicit human approval before indexing |
| Vector / embedding weaknesses | LLM09 Vector and Embedding Weaknesses | Poisoned, unauthorized or irrelevant chunks are retrieved | Metadata allow-list filter; approved-only retrieval; similarity threshold; top-K cap |

**Terminology note:** “Excessive data exposure” is a useful application-security description, but it is not a standalone title in the OWASP 2026 LLM Top 10. This demo addresses it primarily through LLM08 Hidden Context Exposure and LLM02 Sensitive Information Disclosure. OWASP numbering and titles changed between editions; do not mix 2025 and 2026 IDs.

## 5. Vulnerability details and defensive design

### 5.1 Prompt injection

An attacker writes instructions such as `ignore all previous instructions` or embeds instructions inside a document. The model may treat those strings as instructions even though they are untrusted content.

**Defenses in this demo:**
- Known direct-injection phrases are rejected before any model call.
- The system prompt states that customer messages and retrieved passages are untrusted data.
- Retrieved content is labelled as approved source data.
- The model has no tools that can perform business actions.

**Limitation:** phrase matching is bypassable through paraphrases, encoding, multilingual text, obfuscation and indirect injection. Treat it as a teaching example, not a complete injection detector.

### 5.2 Sensitive information disclosure

Sensitive information can enter through user prompts, retrieved documents, conversation memory, tools, logs or model output.

**Defenses in this demo:**
- Card-like numbers are rejected before embeddings or chat completion.
- Only public FAQ content is indexed; customer profile/order/payment data is not connected.
- The assistant is instructed never to request passwords, OTPs or full card numbers.
- A post-generation filter redacts likely secrets, email addresses and phone numbers.

**Limitation:** regular expressions are not a full DLP solution. Use a dedicated data-classification and DLP pipeline for real deployments.

### 5.3 Excessive data / hidden context exposure

A support question generally needs one or two policy paragraphs, not an entire customer database or all documents. Large context windows increase leakage risk, prompt-injection surface and cost.

**Defenses in this demo:**
- Retrieve no more than three documents.
- Apply a similarity threshold and a metadata filter.
- Cap the assembled context at 3,500 characters.
- Do not place secrets in system prompts.
- Do not expose customer records to the model.

### 5.4 Data and model poisoning

A knowledge-base poisoning attack introduces false or malicious content into the corpus that is later retrieved by RAG. This is distinct from changing the base model's training weights. The demo focuses on poisoning the application's knowledge corpus; it does not fine-tune or alter Llama's weights.

**Defenses in this demo:**
- Admin-only ingestion endpoint.
- Size limits and simple instruction-like content checks.
- Newly submitted documents stay pending and are not searchable.
- A separate admin approval action is required before indexing.

**Production additions:** trusted source allow-list, document signatures/checksums, reviewer identity, versioning, audit trail, rollback and periodic corpus scans.

### 5.5 Vector and embedding weaknesses

A vector database can return a malicious chunk, a chunk from the wrong tenant, or a weakly related chunk. An embedding does not prove that content is true or authorized.

**Defenses in this demo:**
- All seeded documents have `tenant=public` and `approved=true` metadata.
- Retrieval always applies `tenant == 'public' && approved == true`.
- `topK(3)` and a similarity threshold bound the returned context.
- Pending content is not embedded/indexed until approval.

**Production additions:** per-tenant ACL filters derived from authenticated identity (never from a user-supplied tenant ID), source-level access checks, deletion propagation, embedding-model version tracking, retrieval evaluation and index integrity monitoring.

## 6. Why the LLM does not have tools

A customer-support assistant may eventually call order, billing, refund or communication services. Giving the LLM direct permission to perform those actions creates excessive-agency risk. This demo deliberately exposes no tools to the model.

A production design should use a separate application service to authorize each action, validate business rules, require human approval for sensitive operations, and pass only the minimum necessary result back to the LLM. The model should never decide its own authorization.

## 7. Token and context budgeting

The application limits output generation through Ollama's `num-predict: 256`, bounds retrieved context, and reports a rough token estimate using four characters per token. Tokenization differs by model and language, so this is only a display estimate. For exact accounting, read the Ollama response's prompt-evaluation and generation-evaluation counters and persist them as telemetry.

## 8. Why this is not production-ready

- Demo Basic Auth credentials are hard-coded and un-hashed.
- SimpleVectorStore is memory-only and not designed for production.
- H2 is in-memory.
- Prompt-injection and PII detectors are deliberately simple.
- The review queue is in memory and resets on restart.
- There is no multi-tenant private-document store, human agent console, durable chat history or production DLP.

The point of this sample is to make the control locations visible in Java and to provide repeatable curl demonstrations, not to certify a system as secure.

## 9. Further reading

- OWASP GenAI Security Project — LLM Top 10: https://genai.owasp.org/llm-top-10/
- OWASP 2026 source repository: https://github.com/GenAI-Security-Project/GenAI-LLM-Top10
- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- Spring AI Ollama Chat: https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html
- Spring AI Vector Stores: https://docs.spring.io/spring-ai/reference/api/vectordbs.html
