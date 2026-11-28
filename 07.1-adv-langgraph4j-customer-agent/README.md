# Customer Support Multi-Agent System — LangGraph4j + Spring AI + Ollama

A teaching/demo project with **three independent Spring Boot agent applications** and one **LangGraph4j orchestrator**. The orchestrator reads `support-workflow.xml`, builds a LangGraph4j `StateGraph`, and routes work between the agents.

## 1. What is included?

| Application | Port | Responsibility | Its own LLM configuration |
|---|---:|---|---|
| `customer-agent` | 8081 | Understand and classify the customer request | `CUSTOMER_MODEL` |
| `billing-agent` | 8082 | Check the demo payment facts and duplicate-charge condition | `BILLING_MODEL` |
| `policy-agent` | 8083 | Evaluate the demo policy / refund eligibility | `POLICY_MODEL` |
| `support-orchestrator` | 8080 | Load XML, build and execute the LangGraph4j graph | No LLM; orchestration only |

The three agent projects each have their own `pom.xml`, `application.yml`, controller, service, prompt, and port. They can be built and deployed independently. The root Maven POM is provided only to make building all four projects convenient.

## 2. Architecture

```text
                 POST /api/support
                        |
                        v
             +----------------------+
             |  Support Orchestrator|
             |  LangGraph4j         |
             |  XML -> StateGraph   |
             +----------+-----------+
                        |
                  customerAgent
                        |
             +----------+-----------+
             |                      |
      PAYMENT_ISSUE           POLICY_QUESTION
             |                      |
             v                      v
      +-------------+        +-------------+
      | Billing     |        | Policy      |
      | Agent       |        | Agent       |
      | LLM 2       |        | LLM 3       |
      +------+------+        +------+------+
             |                      |
       REFUND_REQUIRED        POLICY_EVALUATED
             |                      |
             v                      |
      +-------------+               |
      | Policy      |               |
      | Agent       |               |
      +------+------+---------------+
             |
             v
      +------------------+
      | Final Response   |
      +------------------+
```

General questions can go directly from `customerAgent` to `finalResponse`. A payment issue goes through billing. If billing determines that policy review is required, it routes to policy.

## 3. Concepts

### Agent

An agent is a focused component with a defined responsibility. It typically combines:
- An instruction/prompt describing its role.
- An LLM call for language understanding or reasoning.
- Tools or business services for facts and actions.
- A structured result that another component can use.

In this sample, the three agents are separate REST applications, not just three Java classes in one process.

### Why separate projects?

Separate projects provide independent deployment, scaling, model configuration, prompt changes, and failure boundaries. For example, billing can use a smaller model while policy uses a more capable model.

### LLM versus business rules

An LLM interprets language and helps explain information. It must not be treated as the source of truth for payment status or refund execution. The sample therefore uses simple deterministic demo rules for duplicate detection and refund eligibility, while still calling the configured LLM for agent analysis.

Replace these demo rules with real payment-provider APIs, transaction databases, and approved policy services before production use.

### LangGraph4j

LangGraph4j represents the workflow as a graph:
- **State**: shared data passed between nodes (`customerId`, message, agent results, route).
- **Node**: a unit of work; here, a node calls one agent REST API.
- **Edge**: a connection between nodes.
- **Conditional edge**: chooses the next node based on a value in shared state.
- **START / END**: graph entry and termination markers.
- **Compile**: validates and creates the runnable graph.
- **Invoke**: executes the graph for one request.

### XML workflow

`support-orchestrator/src/main/resources/support-workflow.xml` describes:
- The start node.
- The available nodes.
- Transitions and their condition names.
- The end node.

`XmlWorkflowLoader` parses this XML. `SupportGraphBuilder` translates it into LangGraph4j nodes and edges. The XML is an application-specific workflow definition; it is **not** a built-in LangGraph4j XML format.

The sample graph builder supports the four node IDs in this project (`customerAgent`, `billingAgent`, `policyAgent`, `finalResponse`). It reads the transitions from XML and creates conditional edges for nodes with multiple outgoing transitions.

### Shared state

Each agent returns a JSON object. The orchestrator stores each result in the graph state so later nodes can inspect previous results. This is workflow state for one execution, not persistent conversation memory.

## 4. Technology versions

- Java 21
- Spring Boot 3.5.7
- Spring AI 1.1.1
- LangGraph4j 1.9.10
- Ollama (local LLM runtime)
- Maven

Versions are pinned in the root `pom.xml`. If you upgrade one framework, check its compatibility notes before changing the version.

## 5. Prerequisites on Windows

Install:
1. JDK 21 and confirm `java -version`.
2. Apache Maven 3.9+ and confirm `mvn -version`.
3. Ollama for Windows.
4. An Ollama chat model. This README uses `qwen2.5:3b` as a modest local example.

Open PowerShell and run:

```powershell
java -version
mvn -version
ollama --version
ollama pull qwen2.5:3b
```

Make sure Ollama is running. The Ollama desktop application normally starts its local service. If needed, open another terminal and run:

```powershell
ollama serve
```

Default Ollama URL: `http://localhost:11434`.

## 6. Build all four applications

From the extracted project root (the folder containing the root `pom.xml`):

```powershell
mvn clean package -DskipTests
```

The four executable JARs will be created under each module's `target` folder.

## 7. Start the applications — four PowerShell terminals

Open **four separate PowerShell windows**. In each window, change directory to the extracted project root.

### Terminal 1 — Customer Agent

```powershell
java -jar .\customer-agent\target\customer-agent-1.0.0.jar
```

### Terminal 2 — Billing Agent

```powershell
java -jar .\billing-agent\target\billing-agent-1.0.0.jar
```

### Terminal 3 — Policy Agent

```powershell
java -jar .\policy-agent\target\policy-agent-1.0.0.jar
```

### Terminal 4 — Orchestrator

```powershell
java -jar .\support-orchestrator\target\support-orchestrator-1.0.0.jar
```

Wait until all four Spring Boot applications report that they have started.

### Optional: use different models for each agent

Set environment variables **before starting the relevant application**. For example, in the Customer Agent terminal:

```powershell
$env:CUSTOMER_MODEL = "qwen2.5:3b"
java -jar .\customer-agent\target\customer-agent-1.0.0.jar
```

Billing Agent:

```powershell
$env:BILLING_MODEL = "qwen2.5:3b"
java -jar .\billing-agent\target\billing-agent-1.0.0.jar
```

Policy Agent:

```powershell
$env:POLICY_MODEL = "qwen2.5:3b"
java -jar .\policy-agent\target\policy-agent-1.0.0.jar
```

The model must already be available in Ollama (`ollama pull <model>`). You can use different model names, provided each is installed.

## 8. Test with Windows curl commands

Use **`curl.exe`**, not the PowerShell `curl` alias. Run these from any PowerShell window.

### Test 1 — Duplicate payment (Customer -> Billing -> Policy -> Final)

curl.exe -X POST "http://localhost:8080/api/support" ^
  -H "Content-Type: application/json" ^
  -d "{\"customerId\":\"C1001\",\"message\":\"I was charged twice for subscription SUB1001. Please check the duplicate payment and refund eligibility.\"}"

Expected route:
```text
START
  -> customerAgent
  -> billingAgent
  -> policyAgent
  -> finalResponse
  -> END
```

### Test 2 — General policy question (Customer -> Policy -> Final)

curl.exe -X POST "http://localhost:8080/api/support" ^
  -H "Content-Type: application/json" ^
  --data-raw "{\"customerId\":\"C1002\",\"message\":\"What is your refund policy and eligibility terms?\"}"
  

Expected route:
```text
START
  -> customerAgent
  -> policyAgent
  -> finalResponse
  -> END
```

### Test 3 — General query (Customer -> Final)

curl.exe -X POST "http://localhost:8080/api/support" ^
  -H "Content-Type: application/json" ^
  --data-raw "{\"customerId\":\"C1002\",\"message\":\"What is your refund policy and eligibility terms?\"}"

Expected route:
```text
START
  -> customerAgent
  -> finalResponse
  -> END
```

## 9. Health checks

```powershell
curl.exe "http://localhost:8080/api/support/health"
curl.exe "http://localhost:8081/api/agent/health"
curl.exe "http://localhost:8082/api/agent/health"
curl.exe "http://localhost:8083/api/agent/health"
```

## 10. Directly test each agent

The orchestrator normally calls the agents. You can also call them directly for learning/debugging.

curl.exe -X POST "http://localhost:8081/api/agent/analyze" ^
  -H "Content-Type: application/json" ^
  --data-raw "{\"customerId\":\"C1001\",\"message\":\"I was charged twice for subscription SUB1001\"}"

curl.exe -X POST "http://localhost:8082/api/agent/analyze" ^
  -H "Content-Type: application/json" ^
  --data-raw "{\"customerId\":\"C1001\",\"message\":\"I was charged twice for subscription SUB1001\"}"

curl.exe -X POST "http://localhost:8083/api/agent/analyze" ^
  -H "Content-Type: application/json" ^
  --data-raw "{\"customerId\":\"C1001\",\"message\":\"Please check refund eligibility\",\"billingContext\":\"{\\\"refundRequired\\\":true}\"}"

## 11. Important files to study

| File | What to learn |
|---|---|
| `support-workflow.xml` | Workflow topology and condition names |
| `XmlWorkflowLoader.java` | Parsing XML into Java workflow definitions |
| `SupportState.java` | Shared graph state and channels |
| `SupportGraphBuilder.java` | StateGraph, nodes, edges, conditional routing, invocation |
| `AgentRestClient.java` | Calling separate agent services |
| Each agent's `AgentService.java` | Role prompt, LLM call, business rules |
| Each agent's `application.yml` | Independent model and port configuration |

## 12. Troubleshooting

**`mvn` is not recognized**
- Install Maven and add its `bin` directory to Windows `PATH`.
- Open a new terminal and run `mvn -version`.

**`java` is not recognized or wrong version**
- Install JDK 21.
- Set `JAVA_HOME` to the JDK installation directory and add `%JAVA_HOME%\bin` to `PATH`.

**Ollama connection refused**
- Start Ollama.
- Check `http://localhost:11434`.
- Run `ollama list` and confirm the model exists.

**Model not found**
- Run `ollama pull llama3.2:latest`.
- Confirm the model name in the relevant agent's environment variable or `application.yml`.

**Connection refused to agent port**
- Confirm that the corresponding agent JAR is running.
- Confirm ports 8081, 8082 and 8083 are not being used by another process.

**Orchestrator cannot load XML**
- Confirm `support-workflow.xml` is under `support-orchestrator/src/main/resources`.
- Rebuild with `mvn clean package -DskipTests`.

## 13. Production improvements

This is a learning sample, not a production payment system. Before production:
- Replace keyword-based duplicate detection with a payment ledger/provider API.
- Use authenticated service-to-service communication and validate customer ownership.
- Make refund execution an explicit, authorized tool/action with idempotency keys and audit logs.
- Use structured LLM output with schema validation; never let free-form model text decide money movement.
- Add timeouts, retries, circuit breakers, tracing, metrics, and centralized logs.
- Add tests for each route and XML validation.
- Persist workflow state/checkpoints if workflows must survive process restarts.
- Add human approval before irreversible financial actions.

## 14. Official references

- LangGraph4j getting started: https://langgraph4j.github.io/langgraph4j/main/getting-started/
- LangGraph4j core API: https://langgraph4j.github.io/langgraph4j/main/core/core-library/
- Spring AI Ollama chat: https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html
- Ollama: https://ollama.com/

---
This sample demonstrates orchestration and agent collaboration. The sample policy and payment rules are intentionally simplified and must not be used to make real refund decisions.
