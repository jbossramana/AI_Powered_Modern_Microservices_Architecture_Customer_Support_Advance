# Enterprise Customer Support Microservices — 100% Local, No Docker

This is a teaching reference for Saga choreography, RAG, MCP, supervised ML, Drools and bounded agentic AI. Saga progression uses Kafka choreography. Run Kafka locally as a native Windows process (no Docker). Java services keep demo state in JVM memory. RAG uses Spring AI `SimpleVectorStore` in memory. The ML API is a local Python process and trains its illustrative model in memory at startup. Ollama is installed and run directly on Windows.

> **Data is volatile:** restarting a service clears its in-memory orders, inventory/payment ledgers, ticket data and RAG vectors. This is intentional for a zero-infrastructure learning setup, not production durability.

## Version compatibility note

The parent POM records the requested Spring Boot **4.1.0** and Spring AI **2.1.0-RC1**. Spring AI 2.1.0-RC1 targets the Spring Boot 4.2 baseline, so this exact pair may fail dependency resolution or runtime compatibility checks. For a Boot 4.1 learning baseline, use a Spring AI 2.0.x release compatible with Boot 4.1; alternatively retain Spring AI 2.1.0-RC1 and move Boot to its documented 4.2 baseline. No claim is made that the requested pair has been compiled successfully.

## Architecture

```text
Windows CMD / curl
       |
       v
Order service :8081 -- Drools + publishes OrderCreated
   |
   +-- Kafka topics (local KRaft broker :9092)
         |-- Inventory :8082 consumes order.created
         |      publishes inventory.reserved / inventory.rejected
         |-- Payment :8083 consumes inventory.reserved
         |      publishes payment.authorized / payment.failed
         +-- Order consumes payment outcome; on failure publishes order.cancelled
                Inventory consumes cancellation and publishes inventory.released

AI orchestrator :8084 -- Ollama llama3.2
   |-- RAG --> SimpleVectorStore (in-memory vectors; nomic-embed-text)
   |-- MCP client --> MCP server :8085 (tools)
   |                         |-- local ticket/status tools
   |                         +-- ML HTTP call --> Python :8090
   +-- Agentic LLM tool-selection loop (Spring AI)
```

## Components and ports

| Component | Port | Storage / role |
|---|---:|---|
| order-service | 8081 | In-memory Saga/order state; Drools policy; Kafka event producer/consumer |
| inventory-service | 8082 | In-memory stock/reservation ledger; Kafka consumer/producer |
| payment-service | 8083 | In-memory simulated payment ledger; Kafka consumer/producer |
| ai-service | 8084 | Ollama, RAG `SimpleVectorStore`, MCP client, agent |
| mcp-server | 8085 | MCP tools; in-memory demo ticket data |
| ml-service | 8090 | Local Python FastAPI; TF-IDF + LogisticRegression trained at startup |

## Prerequisites (Windows)

- JDK 21
- Maven 3.9+
- Python 3.10+ (Python launcher `py` recommended)
- Ollama for Windows
- Internet access once to download Maven/Python dependencies and Ollama models

No Docker Desktop, database server, container runtime or cloud account is needed.

## 1. Start Ollama directly on Windows

Open Command Prompt:

```bat
ollama pull llama3.2
ollama pull nomic-embed-text
ollama serve
```

Keep that window open. `llama3.2` is the chat model; `nomic-embed-text` is the embedding model used by RAG. Check with `ollama list`.

## 2. Start Kafka locally (no Docker)

Kafka is the event broker for the choreography. Download an Apache Kafka binary distribution for Windows and extract it, for example to `C:\kafka`. Open Command Prompt in that directory. For a fresh KRaft standalone broker (Kafka 4.x):

```bat
cd /d C:\kafka
kafka-storage.bat random-uuid
```

Copy the generated cluster ID, then format the storage (replace `PASTE_CLUSTER_ID`):

```bat
kafka-storage.bat format --standalone -t cluster-id -c ..\..\config\server.properties
set KAFKA_HEAP_OPTS=-Xms1G -Xmx1G
kafka-server-start.bat ..\..\config\server.properties
```

Keep this window open. The default broker listener is `localhost:9092`. Kafka writes its own log segments to the configured `log.dirs`; the application business data remains in JVM memory. To make broker data disposable, set `log.dirs` in `config\server.properties` to a temporary directory such as `C:/kafka-data-demo`, stop Kafka, then delete that directory before reformatting/restarting. Do not delete it while Kafka is running.

The broker is a required process for a multi-process Kafka choreography. `@EmbeddedKafka` is appropriate for integration tests, not as a single shared broker across independently started services.

Create all Saga topics
======================

kafka-topics.bat --bootstrap-server localhost:9092 --create --topic order.created --partitions 3 --replication-factor 1

kafka-topics.bat --bootstrap-server localhost:9092 --create --topic inventory.reserved --partitions 3 --replication-factor 1

kafka-topics.bat --bootstrap-server localhost:9092 --create --topic inventory.failed --partitions 3 --replication-factor 1

kafka-topics.bat --bootstrap-server localhost:9092 --create --topic payment.authorized --partitions 3 --replication-factor 1

kafka-topics.bat --bootstrap-server localhost:9092 --create --topic payment.failed --partitions 3 --replication-factor 1

kafka-topics.bat --bootstrap-server localhost:9092 --create --topic order.completed --partitions 3 --replication-factor 1

kafka-topics.bat --bootstrap-server localhost:9092 --create --topic order.cancelled --partitions 3 --replication-factor 1

kafka-topics.bat --bootstrap-server localhost:9092 --create --topic inventory.released --partitions 3 --replication-factor 1

## 3. Start the local supervised ML API

Open another Command Prompt, from the project root:

```bat
cd ml-service
py -3 -m venv .venv
.venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
uvicorn app:app --host 127.0.0.1 --port 8090
```

Alternatively run `ml-service\run-windows.bat`. The classifier trains from the tiny sample dataset when the Python process starts. It is illustrative, not production-trained.

## 4. Build the Java services

From the project root:

```bat
mvn -U clean package -DskipTests
```

If the requested Boot 4.1 / AI 2.1 RC combination is incompatible, adjust the two parent POM properties as described above and rebuild.

## 5. Start the five Spring Boot services

Open five separate Command Prompt windows at the project root and run one command per window:

```bat
mvn -pl inventory-service spring-boot:run
```

```bat
mvn -pl payment-service spring-boot:run
```

```bat
mvn -pl mcp-server spring-boot:run
```

```bat
mvn -pl ai-service spring-boot:run
```

```bat
mvn -pl order-service spring-boot:run
```

Check health:

```bat
curl.exe http://localhost:8081/actuator/health
curl.exe http://localhost:8082/actuator/health
curl.exe http://localhost:8083/actuator/health
curl.exe http://localhost:8084/actuator/health
curl.exe http://localhost:8085/actuator/health
curl.exe http://localhost:8090/health
```

## Windows CMD curl examples

**Important:** In Windows Command Prompt, escape the JSON double quotes as `\"` inside the `-d` argument. These examples are for `cmd.exe`, not PowerShell.

### A. Successful Saga order

```bat
curl.exe -X POST "http://localhost:8081/api/orders" -H "Content-Type: application/json" -d "{\"customerId\":\"C100\",\"itemId\":\"SKU-101\",\"quantity\":2,\"amount\":4999.00,\"paymentToken\":\"demo-token\"}"
```

Expected initial response: `PENDING` with a `sagaId`. Kafka events then drive inventory reservation, payment authorization and order completion asynchronously. Poll the Saga status using the returned ID.

### B. Payment failure and Saga compensation

```bat
curl.exe -X POST "http://localhost:8081/api/orders" -H "Content-Type: application/json" -d "{\"customerId\":\"C100\",\"itemId\":\"SKU-101\",\"quantity\":1,\"amount\":1200.00,\"paymentToken\":\"FAIL\"}"
```

Expected: inventory is reserved, payment fails, Order publishes `order.cancelled`, Inventory releases its reservation, and publishes `inventory.released`.

Poll Saga status (replace `SAGA_ID` with the ID returned by the POST):

```bat
curl.exe "http://localhost:8081/api/orders/SAGA_ID"
```

### C. Drools manual-review rule

```bat
curl.exe -X POST "http://localhost:8081/api/orders" -H "Content-Type: application/json" -d "{\"customerId\":\"C200\",\"itemId\":\"SKU-900\",\"quantity\":1,\"amount\":15000.00,\"paymentToken\":\"demo-token\"}"
```

The sample policy sends orders above the configured threshold to manual review.

### D. Supervised ML priority prediction

```bat
curl.exe -X POST "http://localhost:8090/api/ml/predict-priority" -H "Content-Type: application/json" -d "{\"subject\":\"Production system down\",\"body\":\"All customers cannot login and payments are failing\",\"planTier\":\"BUSINESS\",\"accountAgeDays\":400,\"priorTicketCount30d\":4}"
```

### E. Ingest knowledge into the in-memory RAG vector store

```bat
curl.exe -X POST "http://localhost:8084/api/knowledge/ingest" -H "Content-Type: application/json" -d "{\"source\":\"refund-policy-v1\",\"text\":\"Refund policy: duplicate payment claims must be checked against the payment ledger. A verified duplicate charge is eligible for refund after account ownership is confirmed. Standard refund processing takes 5 to 7 business days.\"}"
```

### F. Ask a grounded RAG question

```bat
curl.exe -X POST "http://localhost:8084/api/knowledge/ask" -H "Content-Type: application/json" -d "{\"question\":\"What is the processing time for a verified duplicate payment refund?\"}"
```

### G. Ask the agent to investigate using MCP tools

```bat
curl.exe -X POST "http://localhost:8084/api/agent/run" -H "Content-Type: application/json" -d "{\"goal\":\"Investigate ticket T-1001. Look up its details, check payment service status, and summarize the next safe support action. Do not issue a refund.\"}"
```

## In-memory / local design decisions

- **No Docker:** no compose file or Dockerfile is included.
- **No external database:** no PostgreSQL, MySQL, MongoDB or Redis.
- **RAG:** Spring AI `SimpleVectorStore` stores vectors in the AI service JVM heap. It calls Ollama's embedding model for vector creation. Restarting `ai-service` clears the index; re-run the ingest curl commands.
- **Saga choreography:** Order, Inventory and Payment independently publish/consume Kafka events. No central orchestrator calls participant REST endpoints. Service business state is held in local Java collections. Each service is an independent process, so memory is not shared. Kafka is a local broker process and stores event logs in its configured log directory.
- **ML:** FastAPI runs locally on Windows, not in a container. Its sample model is trained in process memory at startup.
- **MCP:** MCP server runs as a normal Spring Boot process and exposes tools over local HTTP.
- **Ollama:** runs as a native Windows background/server process and stores downloaded model files on the local machine. It is not embedded in the Java process.

## Reset behavior

Stop and restart a service to clear its in-memory state. For RAG, restart `ai-service` and re-ingest knowledge. For ML, restart the Python process to retrain the sample classifier. There are no database files to delete.

## Important implementation boundaries

- Saga choreography is event-driven: each participant reacts to events and publishes the next event. Compensation is a new business operation, not a distributed rollback. See `docs/KAFKA-CHOREOGRAPHY.md`.
- Drools enforces deterministic policy; the LLM must not override a mandatory rule.
- ML predicts from labelled examples; temperature/top-p/top-k do not tune the classifier.
- RAG retrieves context; it does not fine-tune the LLM or guarantee factual answers.
- MCP standardizes tool discovery/invocation; it is not itself an autonomous agent.
- Agentic behavior uses a goal and tool callbacks. Apply explicit tool permissions, iteration limits, timeouts and human approval for sensitive actions in production.

## Kafka Windows CMD test / troubleshooting

List topics:

```bat
kafka-topics.bat --bootstrap-server localhost:9092 --list
```

If no events flow, confirm the broker is running on port 9092, all three service `application.yml` files point to `localhost:9092`, and the consumer groups have started. The broker's default auto-topic-creation can create the sample topics when the first event is sent.

## Troubleshooting

- Ollama connection refused: run `ollama serve` and verify `ollama list`.
- Model not found: run `ollama pull llama3.2` and `ollama pull nomic-embed-text`.
- RAG embedding failure: confirm Ollama serves `nomic-embed-text`; this is separate from the chat model.
- MCP tools not discovered: ensure MCP server is running on 8085 and inspect AI service startup logs and configured `/mcp` endpoint.
- ML connection refused: activate the Python virtual environment and start Uvicorn on 8090.
- Maven compatibility errors: align Spring Boot and Spring AI release baselines; see version note above.

## Production evolution (not part of this no-infrastructure demo)

For production, replace volatile memory with durable per-service storage, add a transactional outbox, persistent Saga state, idempotency keys, retries, authentication/authorization, audit logging, observability, RAG access-control metadata and model evaluation. These are intentionally omitted to keep this project fully local and in-memory.
