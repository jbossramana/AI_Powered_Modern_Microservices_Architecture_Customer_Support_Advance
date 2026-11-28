# AI Gateways and LiteLLM

## 1. What is an AI Gateway?

An **AI Gateway** (also called an **LLM Gateway**) is an intermediary
layer between an application and one or more AI model providers or
model-serving systems.

Instead of connecting every application directly to OpenAI, Anthropic,
Gemini, Ollama, or another model endpoint, an application sends requests
to a common gateway endpoint. The gateway can then apply routing,
authentication, usage controls, logging, retries, and other policies
before forwarding the request.

### Typical request flow

``` text
Application
    |
    v
AI Gateway
    |-- Authentication and API-key validation
    |-- Model selection and routing
    |-- Rate limits and usage controls
    |-- Retries and fallback policies
    |-- Logging, metrics and cost tracking
    |-- Optional caching and guardrail integrations
    |
    v
Model provider / model runtime
    |-- Cloud model API
    |-- Self-hosted model server
    |-- Local Ollama
    |
    v
Model response
```

An AI Gateway is not itself necessarily an LLM. It normally routes
requests to an LLM runtime or provider. For example, **LiteLLM is the
gateway/proxy**, while **Ollama runs the local Llama 3.2 model**.

## 2. Why use an AI Gateway?

1.  **Provider abstraction** -- applications use a consistent API
    instead of implementing a separate integration for every provider.
2.  **Centralized routing** -- choose a model or provider based on
    configuration or routing policy.
3.  **Resilience** -- retry requests or route to a fallback deployment
    when a configured upstream fails.
4.  **Access control** -- validate gateway keys and manage application
    or team access.
5.  **Usage governance** -- apply request/token limits, budgets, and
    usage policies where supported.
6.  **Observability** -- collect request logs, token usage, latency,
    errors, and cost information.
7.  **Caching** -- avoid repeated model calls for eligible requests.
8.  **Policy integrations** -- connect request/response guardrails,
    content checks, and PII detection.
9.  **Operational separation** -- keep model-provider configuration
    outside application business logic.

Not every gateway provides every feature in every edition. Verify
product documentation, edition, and deployment requirements before
selecting a gateway.

## 3. AI Gateway tools

The following are examples of commonly discussed AI Gateway products and
projects. They are not identical products: some are self-hosted proxies,
some extend API-management platforms, and some are managed services.

  --------------------------------------------------------------------------------
  Gateway                 General approach          Common strengths / fit
  ----------------------- ------------------------- ------------------------------
  **LiteLLM**             Self-hostable Python      OpenAI-compatible API, broad
                          proxy and SDK             provider integrations, model
                                                    routing, retries/fallbacks,
                                                    virtual keys, budgets, usage
                                                    tracking and integrations

  **Portkey AI Gateway**  Gateway with managed and  Routing, retries, fallbacks,
                          self-hosted/open-source   observability, guardrails and
                          options                   governance features; exact
                                                    availability depends on
                                                    plan/deployment

  **Kong AI Gateway**     AI capabilities built     Useful where an organization
                          into Kong API Gateway     already uses Kong for API
                                                    management, plugins, traffic
                                                    policies and enterprise API
                                                    governance

  **Envoy AI Gateway /    Gateway built around      Kubernetes/platform-oriented
  Agent Router**          Envoy and cloud-native    traffic management and
                          infrastructure            provider routing

  **Cloudflare AI         Managed gateway on        Managed logging, analytics,
  Gateway**               Cloudflare's network      caching, rate controls and
                                                    routing features; not a
                                                    self-hosted local proxy

  **Vercel AI Gateway**   Managed model access and  Convenient for applications in
                          routing                   the Vercel ecosystem and teams
                                                    wanting a managed gateway

  **OpenRouter**          Hosted model/provider     Central access to a large
                          routing service           catalogue of hosted models; it
                                                    is not the same as operating
                                                    your own local gateway

  **TrueFoundry AI        AI platform and gateway   Managed or private deployment
  Gateway**               offering                  options, model routing,
                                                    governance and observability
                                                    capabilities

  **Bifrost**             Self-hosted, Go-based AI  Focus on high-throughput
                          gateway                   gateway operation and
                                                    multi-provider routing

  **Helicone**            LLM observability and     Request tracing, debugging,
                          gateway capabilities      usage visibility and provider
                                                    integrations
  --------------------------------------------------------------------------------

### Choosing at a high level

-   Choose **LiteLLM** when you want to run and configure a gateway
    yourself and need a common API across model providers.
-   Consider **Kong AI Gateway** when AI traffic must be governed as
    part of an existing Kong API-management platform.
-   Consider **Cloudflare AI Gateway** or **Vercel AI Gateway** when a
    managed service is preferred over operating the gateway runtime.
-   Consider **Portkey** or **TrueFoundry** when centralized governance,
    managed operations, or enterprise platform features are important.
-   Consider **Envoy AI Gateway / Agent Router** when the platform is
    already based on Envoy and Kubernetes.
-   Consider **OpenRouter** when the main requirement is hosted access
    to many models rather than hosting the gateway and inference runtime
    yourself.

Feature sets, licensing, product names, and pricing change. Treat this
table as an orientation guide, not a contractual feature matrix.

## 4. LiteLLM overview

**LiteLLM** is an AI gateway/proxy that provides a common interface for
calling multiple model providers. Its proxy exposes an OpenAI-compatible
API, allowing compatible applications and SDKs to send requests through
LiteLLM instead of integrating directly with each provider.

### Important LiteLLM capabilities

#### 4.1 OpenAI-compatible API

Applications can use a familiar API shape, for example:

``` http
POST /v1/chat/completions
Content-Type: application/json
Authorization: Bearer <LITELLM_KEY>
```

Example request:

``` json
{
  "model": "llama3.2",
  "messages": [
    {
      "role": "user",
      "content": "Explain Spring AI"
    }
  ]
}
```

The application sends the public model alias configured in LiteLLM.
LiteLLM maps that alias to the actual provider/model identifier.

#### 4.2 Multiple model providers

LiteLLM supports integrations with many hosted and self-hosted model
providers. Examples include OpenAI-compatible services, Anthropic,
Google models, Azure OpenAI, AWS Bedrock, Ollama and other model-serving
systems.

Provider support and individual model capabilities should be checked
against the current LiteLLM documentation.

#### 4.3 Model aliasing and routing

A public model name can be mapped to a provider-specific model
identifier.

Example:

``` yaml
model_list:
  - model_name: llama3.2
    litellm_params:
      model: ollama/llama3.2:latest
      api_base: http://127.0.0.1:11434
```

In this example:

-   `llama3.2` is the model name presented to the application.
-   `ollama/llama3.2:latest` identifies the Ollama provider and actual
    model.
-   `api_base` points to the Ollama server.

The application uses `llama3.2`; it does not need to know the
provider-specific identifier.

#### 4.4 Load balancing, retries and fallbacks

For deployments with multiple model instances or configured
alternatives, LiteLLM can route requests and apply retry/fallback
behavior. This can help handle transient failures and distribute
traffic.

A fallback only works when an appropriate fallback deployment or model
is configured. A single local Llama 3.2 deployment does not provide true
model-level redundancy by itself.

#### 4.5 Virtual keys, budgets and rate limits

LiteLLM provides key-management and usage-control capabilities,
including virtual keys and configurable limits. These capabilities can
help separate application credentials and apply usage policies.

Some advanced key-management and enterprise governance capabilities may
require a database, additional configuration, or an Enterprise offering.
A basic local proxy does not need a database merely to route requests to
Ollama.

#### 4.6 Usage tracking and spend information

LiteLLM can record request and usage information and expose
spend/usage-related capabilities. The detail available depends on
provider usage metadata, configuration, database setup, and edition.

For a local Ollama model, do not assume that cloud-provider monetary
cost calculations have the same meaning: local inference still consumes
hardware, electricity, and operational resources.

#### 4.7 Logging and observability integrations

LiteLLM supports request logging and integrations with
monitoring/observability tools. These can help investigate model errors,
latency, usage and routing behavior.

Avoid logging secrets, personal data, or sensitive prompts unless the
logging policy and data handling controls explicitly allow it.

#### 4.8 Caching

LiteLLM supports caching configurations for eligible requests. Depending
on the cache type and configuration, repeated requests may be served
without invoking the model again.

Caching needs careful design: semantically similar prompts are not
always interchangeable, and cached responses may be inappropriate for
personalized or sensitive requests.

#### 4.9 Guardrails and policy integrations

LiteLLM can be integrated with guardrail and content-checking systems
for request and response processing. The available integrations and
enforcement behavior depend on the configured guardrail provider and
LiteLLM edition.

A gateway guardrail is not a complete security solution. Applications
still need authorization, input validation, output handling,
prompt-injection defenses, and controls around tools and data access.

#### 4.10 MCP gateway capabilities

LiteLLM also documents MCP-related gateway capabilities for centrally
managing access to MCP servers and tools. Treat this as a separate
capability from ordinary model chat-completion routing; confirm the
current feature and edition requirements before relying on it.

## 5. LiteLLM in this Spring Boot project

The project uses three separate components:

``` text
Client
  |
  | X-Gateway-API-Key
  v
Spring Boot AI Gateway (port 8085)
  |
  | OpenAI-compatible request
  | Authorization: Bearer <LiteLLM master key>
  v
LiteLLM Proxy (port 4000)
  |
  | Provider route: ollama/llama3.2:latest
  v
Ollama (port 11434)
  |
  v
Llama 3.2 model
```

### Responsibility of each component

  -----------------------------------------------------------------------
  Component                           Responsibility
  ----------------------------------- -----------------------------------
  Client                              Calls the Spring Boot gateway

  Spring Boot AI Gateway              Application-facing API, client
                                      authentication, request validation
                                      and project-specific guardrails

  LiteLLM                             Model alias resolution, proxying
                                      and configured routing/usage
                                      policies

  Ollama                              Loads and runs the local model

  Llama 3.2                           Generates the response
  -----------------------------------------------------------------------

### Model-name mapping in this project

  Layer                        Value
  ---------------------------- --------------------------
  Ollama model                 `llama3.2:latest`
  LiteLLM public model alias   `llama3.2`
  Spring AI configured model   `llama3.2`
  LiteLLM endpoint             `http://localhost:4000`
  Ollama endpoint              `http://127.0.0.1:11434`

The model alias must match exactly between the LiteLLM configuration and
the model name sent by Spring AI. The alias is not necessarily the same
string as the provider-specific model identifier.

### Why use both Spring Boot Gateway and LiteLLM?

They have different responsibilities:

-   **Spring Boot Gateway** is the application's own API boundary. It
    can enforce project-specific authentication, validation, guardrails,
    response shaping, and business policies.
-   **LiteLLM** is the model connectivity and routing layer. It hides
    provider-specific endpoints and offers model-gateway features.
-   **Ollama** is the local inference runtime.
-   **Llama 3.2** is the model itself.

LiteLLM does not replace the Spring Boot application gateway, and Spring
Boot does not need to implement provider-specific model routing itself.

## 6. What LiteLLM does not automatically provide

LiteLLM should not be treated as a substitute for every AI security and
application concern.

-   It does not guarantee that model outputs are factually correct.
-   It does not automatically prevent all prompt injection or data
    leakage.
-   It does not replace application-level authorization or tenant
    isolation.
-   It does not make an unsafe MCP tool safe by itself.
-   It does not provide high availability when only one Ollama process
    and one model instance exist.
-   It does not remove the need to secure API keys, network access,
    logs, and configuration files.
-   It does not turn local inference into cloud inference; Ollama still
    performs the model execution locally.

## 7. When should an organization introduce an AI Gateway?

An AI Gateway becomes useful when an organization needs one or more of
the following:

-   Multiple applications calling models.
-   More than one model provider or deployment.
-   Centralized credentials and usage policies.
-   Model routing and fallback behavior.
-   Shared observability and cost/usage reporting.
-   Centralized caching or guardrail integrations.
-   A controlled migration between model providers.

For a small application using one local model, a gateway is optional. It
becomes more valuable as the number of applications, model deployments,
teams, and governance requirements increases.

## 8. Summary

An AI Gateway is an infrastructure layer that centralizes how
applications access AI models.

**LiteLLM is a practical option when a team wants a self-hosted proxy
with an OpenAI-compatible interface and configurable model routing.** In
this project, LiteLLM routes Spring AI requests to the local
Ollama-hosted Llama 3.2 model.

Remember the distinction:

-   AI Gateway: routes and governs model requests.
-   LiteLLM: the gateway/proxy used in this project.
-   Ollama: the local model runtime.
-   Llama 3.2: the LLM generating responses.
-   Spring Boot Gateway: the application-facing API and project-specific
    security layer.

## 9. References

-   [LiteLLM documentation](https://docs.litellm.ai/)
-   [LiteLLM Proxy documentation](https://docs.litellm.ai/docs/)
-   [LiteLLM GitHub repository](https://github.com/BerriAI/litellm)
-   [Portkey documentation](https://portkey.ai/docs/)
-   [Kong AI Gateway
    documentation](https://docs.konghq.com/gateway/latest/ai-gateway/)
-   [Envoy AI Gateway documentation](https://aigateway.envoyproxy.io/)
-   [Cloudflare AI Gateway
    documentation](https://developers.cloudflare.com/ai-gateway/)
-   [Vercel AI Gateway
    documentation](https://vercel.com/docs/ai-gateway)
-   [OpenRouter documentation](https://openrouter.ai/docs)
-   [TrueFoundry documentation](https://docs.truefoundry.com/)
-   [Bifrost GitHub repository](https://github.com/maximhq/bifrost)
-   [Vercel: Open-source AI gateways
    compared](https://vercel.com/i/open-source-ai-gateways)
-   [Arize AI: LLM gateway
    comparison](https://arize.com/resources/best-llm-gateways-comparison/)

*Note: Gateway capabilities, product names, licensing, and feature
availability evolve. Check the linked official documentation for the
current release and plan before making an implementation or procurement
decision.*
