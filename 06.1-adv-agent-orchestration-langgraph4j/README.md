# What is Agent Orchestration?

Agent orchestration is the process of coordinating multiple AI agents, tools, LLMs, and workflows to accomplish a task.

Instead of relying on a single LLM to perform everything, an orchestration framework manages:

  * Which agent should execute a task.

  * How agents communicate with each other.

  * What tools an agent can access.

  * How information and state are shared.

  * How execution flows between agents.

  * How errors, retries, and human approvals are handled.

# Example

Consider an AI-powered customer support system:

  * User submits a query.

  * Intent Agent identifies the query type.

  * Order Agent retrieves order information.

  * Refund Agent checks refund eligibility.

  * Response Agent generates the final response.

The orchestration layer controls the execution sequence and passes information between agents.

# What is LangGraph4j?

LangGraph4j is a Java framework inspired by LangGraph that allows developers to build stateful, graph-based AI agent workflows.

It helps implement:

  * Multi-agent systems.

  * Conditional workflows.

  * Sequential and parallel execution.

   * Cyclic agent workflows.

  * Tool calling.

  * State management.

  * Human-in-the-loop workflows.

Rather than implementing orchestration using multiple if-else statements, you define a graph containing nodes and edges.

Each node performs a particular operation, and edges determine what executes next.

# Core concepts of LangGraph4j

There are five important building blocks.

# State

State is the shared memory of the graph execution.

It stores user input, conversation history, intermediate results, tool outputs, and agent responses.

Every node can read the current state and return updates.

Reducers define how updates are applied.

Example:

Map<String, Object> state = Map.of(
    "userQuery", "Where is my order?",
    "intent", "ORDER_STATUS",
    "orderDetails", null
);

Note :  Think of State as a shared context passed between agents.

# Nodes

A node represents an individual processing step.

Examples:

Intent classification node.

LLM response node.

Database retrieval node.

Tool execution node.

Validation node.

A node generally:

Receives the current state.

Executes business logic or an LLM call.

Returns updated state.

Nodes can use Java code, external APIs, tools, or LLMs.

# Edges

Edges determine which node executes next.

There are two important types:

Edge										        Purpose

Normal edge              Always moves to the next node

Conditional edge        Selects the next node based on state or a condition


# StateGraph

StateGraph is the main class used to define the workflow.

It contains:

  * Nodes.

  * Edges.

  * Conditional routing.

  *Entry and exit points.

  * State schema.
  
# CompiledGraph

Once the graph is defined, compile it.

The compiled graph is the executable workflow.

You can invoke it with initial state or use streaming execution to observe intermediate node outputs.



# Agent Orchestration (LangGraph4j + Spring AI)

A single `ChatClient` call can't express "do A, then branch to B or C based on what
happened in A." That's what this module adds: a real graph, with a routing decision
made in code between steps. LangGraph4j owns the *shape* of the workflow (which node
runs next); Spring AI's `ChatClient` still does every actual model call, from inside
plain Spring-managed node classes.

## What this teaches

- Modeling a workflow as a `StateGraph`: nodes, edges, and a **conditional edge** that
  picks the next node based on state a previous node produced
- Defining shared state with `AgentState` + `Channels.base(...)` — simple "last write
  wins" fields, as opposed to an accumulating list like a chat history
- Where Spring AI fits inside a LangGraph4j node: it's just a `ChatClient` call from
  ordinary Java, no special glue required
- Encoding a business-critical routing rule (`routeAfterTriage`) as a plain, testable
  static function instead of trusting a prompt to "always escalate angry tickets"

## The graph

```
        START
          |
       triage  (structured-output classification, same idea as Module 2)
        /    \
 urgent+     everything
 negative      else
    |            |
 escalate   knowledge_retrieval  (simplified retrieval, same idea as Module 3)
    |            |
   END    draft_response  (drafts the customer-facing reply)
                 |
                END
```

## Prerequisites

```bash
ollama pull qwen3:8b
```

## Run it

```bash
cd 05-agent-orchestration-langgraph4j
mvn spring-boot:run
```

## Try it

**Routine ticket** (should auto-draft):

curl -s http://localhost:8080/api/tickets/process -H "Content-Type: application/json" -d "{\"ticketId\":\"TCK-2001\",\"customerId\":\"C-1001\",\"ticketText\":\"Quick question - what happens to my files if I go over my storage limit?\"}"

**Password reset ticket**
curl.exe -s -X POST "http://localhost:8080/api/tickets/process" -H "Content-Type: application/json" -d "{\"ticketId\":\"TCK-2003\",\"customerId\":\"C-1003\",\"ticketText\":\"I forgot my password and cannot sign in. Please help me reset my password.\"}"



**Urgent, angry ticket** (should escalate):

curl -s http://localhost:8080/api/tickets/process -H "Content-Type: application/json" -d "{\"ticketId\":\"TCK-2002\",\"customerId\":\"C-1002\",\"ticketText\":\"This is the third outage this week and nobody has responded. We are moving our whole team off this platform if this is not fixed today.\"}" 


Compare the `route` field and notice `draftResponse` vs. `escalationReason` — only one
of the two branches ran for each request; the other field stays at its schema default.

## How it works

- `SupportGraphState` (in `graph/`) is the state every node reads and writes, via a
  `Map<String,Object>` returned from each node's `apply(...)`.
- Four `NodeAction<SupportGraphState>` implementations — `TriageNode`,
  `KnowledgeRetrievalNode`, `ResponseDraftNode`, `EscalationNode` — are the workflow's
  steps. Each is a plain `@Component`; two of them happen to call the shared
  `ChatClient` bean from `OrchestrationChatConfig`, two don't need to.
- `SupportGraphBuilder` wires them into a `StateGraph`, adds the conditional edge with
  `routeAfterTriage` as the routing function, and compiles it into a `CompiledGraph`
  bean that `OrchestrationController` invokes per request.

## A note on the knowledge retrieval node

`KnowledgeRetrievalNode` uses simple keyword overlap against four hardcoded articles,
not the vector store from Module 3 — this keeps the module focused on graph mechanics
rather than repeating RAG setup. In a real system, swap its body for a call through a
`VectorStoreDocumentRetriever` exactly like Module 3's; nothing about the graph's shape
needs to change, since the node's contract (read `ticketText`, write
`retrievedContext`) stays the same either way.

## What's next

`06-human-in-the-loop-spring-ai` builds a proper approval queue for the kind of
AI-drafted response this graph's `draft_response` node produces — nothing here actually
sends anything to a customer yet, on purpose.
