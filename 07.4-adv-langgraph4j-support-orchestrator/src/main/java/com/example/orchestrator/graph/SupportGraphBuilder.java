
package com.example.orchestrator.graph;

// LangGraph4j predefined START and END nodes.
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

// Helper methods to create asynchronous graph edges and nodes.
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.example.orchestrator.client.AgentRestClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * @Component:
 * Registers SupportGraphBuilder as a Spring Bean.
 *
 * This class is responsible for:
 * 1. Loading workflow configuration from XML.
 * 2. Creating the LangGraph4j state graph.
 * 3. Registering agent nodes.
 * 4. Configuring conditional routing.
 * 5. Executing the graph.
 * 6. Returning the final support response.
 */
@Component
public class SupportGraphBuilder {

    // REST client used to communicate with independent agents.
    private final AgentRestClient agents;

    // Reads and parses support-workflow.xml.
    private final XmlWorkflowLoader loader;

    // Converts Java Maps into JSON strings and vice versa.
    private final ObjectMapper mapper;

    // Compiled graph ready for execution.
    private final CompiledGraph<SupportState> graph;


    /*
     * Constructor injection.
     *
     * Spring injects:
     * - AgentRestClient
     * - XmlWorkflowLoader
     * - ObjectMapper
     * - ResourceLoader
     *
     * The XML file is loaded during application startup.
     * The workflow is then converted into a LangGraph4j graph.
     */
    public SupportGraphBuilder(
            AgentRestClient agents,
            XmlWorkflowLoader loader,
            ObjectMapper mapper,
            org.springframework.core.io.ResourceLoader resourceLoader)
            throws Exception {

        this.agents = agents;
        this.loader = loader;
        this.mapper = mapper;

        // Load workflow configuration from src/main/resources.
        Resource xml =
                resourceLoader.getResource("classpath:support-workflow.xml");

        // Parse XML and build the executable graph.
        this.graph = build(loader.load(xml));
    }


    /*
     * BUILD GRAPH
     *
     * Converts the XML workflow definition into a LangGraph4j StateGraph.
     *
     * The XML defines:
     * - Node names
     * - Starting node
     * - Transitions
     * - Conditional routing
     * - Ending node
     */
    private CompiledGraph<SupportState> build(
            XmlWorkflowLoader.Workflow workflow)
            throws GraphStateException {

        /*
         * StateGraph:
         * Creates a graph that shares SupportState between nodes.
         *
         * SupportState.SCHEMA defines the available state keys.
         * SupportState::new creates state instances.
         */
        StateGraph<SupportState> builder =
                new StateGraph<>(SupportState.SCHEMA, SupportState::new);


        /*
         * REGISTER NODES
         *
         * Each XML node is mapped to a Java method.
         *
         * customerAgent -> customerNode()
         * billingAgent  -> billingNode()
         * policyAgent   -> policyNode()
         * finalResponse -> finalNode()
         *
         * node_async() adapts each method into an asynchronous
         * LangGraph4j node action.
         */
        for (var node : workflow.nodes()) {

            switch (node.id()) {

                case "customerAgent" ->
                    builder.addNode("customerAgent",
                            node_async(this::customerNode));

                case "billingAgent" ->
                    builder.addNode("billingAgent",
                            node_async(this::billingNode));

                case "policyAgent" ->
                    builder.addNode("policyAgent",
                            node_async(this::policyNode));

                case "finalResponse" ->
                    builder.addNode("finalResponse",
                            node_async(this::finalNode));

                default ->
                    throw new IllegalArgumentException(
                            "Unsupported workflow node: " + node.id());
            }
        }


        /*
         * START EDGE
         *
         * Every workflow starts from the LangGraph4j START node.
         *
         * The XML determines the first application node.
         * In our example, it is customerAgent.
         */
        builder.addEdge(START, workflow.start());


        /*
         * GROUP TRANSITIONS BY SOURCE NODE
         *
         * Example XML:
         *
         * customerAgent -> billingAgent
         * customerAgent -> policyAgent
         * customerAgent -> finalResponse
         *
         * All transitions originating from customerAgent
         * are grouped together.
         *
         * LinkedHashMap preserves insertion order.
         */
        Map<String, List<XmlWorkflowLoader.Transition>> bySource =
                workflow.transitions().stream()
                        .collect(Collectors.groupingBy(
                                XmlWorkflowLoader.Transition::from,
                                LinkedHashMap::new,
                                Collectors.toList()));


        /*
         * CONFIGURE GRAPH EDGES
         *
         * Two possibilities:
         *
         * 1. Single outgoing transition:
         *    Create a normal edge.
         *
         * 2. Multiple outgoing transitions:
         *    Create conditional edges based on the route value
         *    stored in SupportState.
         */
        for (var entry : bySource.entrySet()) {

            String source = entry.getKey();

            List<XmlWorkflowLoader.Transition> outgoing =
                    entry.getValue();


            // Case 1: Only one outgoing transition.
            if (outgoing.size() == 1) {

                // Example: policyAgent -> finalResponse.
                builder.addEdge(source, outgoing.get(0).to());

            } else {

                /*
                 * Case 2: Multiple outgoing transitions.
                 *
                 * Create a mapping:
                 *
                 * DUPLICATE_PAYMENT -> billingAgent
                 * PAYMENT_ISSUE     -> billingAgent
                 * POLICY_QUESTION   -> policyAgent
                 * GENERAL_QUERY     -> finalResponse
                 */
                Map<String, String> mappings = outgoing.stream()
                        .collect(Collectors.toMap(
                                XmlWorkflowLoader.Transition::condition,
                                XmlWorkflowLoader.Transition::to,
                                (a, b) -> a,
                                LinkedHashMap::new));


                /*
                 * Conditional routing:
                 *
                 * edge_async() reads the "route" value from
                 * SupportState.
                 *
                 * LangGraph4j compares this value with the
                 * XML transition conditions and selects
                 * the corresponding destination node.
                 */
                builder.addConditionalEdges(
                        source,
                        edge_async(state -> state.text("route")),
                        mappings);
            }
        }


        /*
         * END EDGE
         *
         * Connect the XML-defined final node to LangGraph4j END.
         *
         * In this workflow:
         * finalResponse -> END
         */
        builder.addEdge(workflow.end(), END);


        // Compile the StateGraph into an executable graph.
        return builder.compile();
    }


    /*
     * RUN WORKFLOW
     *
     * Entry point called by the Support REST Controller.
     *
     * Accepts:
     * - customerId
     * - customer message
     *
     * Returns:
     * - customer analysis
     * - billing analysis
     * - policy analysis
     * - final response
     */
    public Map<String, Object> run(
            String customerId,
            String message) {

        try {

            /*
             * INITIAL STATE
             *
             * Create the initial input for the graph.
             *
             * These values become available to all nodes
             * through SupportState.
             */
            Map<String, Object> input = Map.of(
                    "customerId", customerId,
                    "message", message
            );


            /*
             * GRAPH EXECUTION
             *
             * GraphInput.args() supplies initial state values.
             *
             * RunnableConfig.empty() uses the default execution
             * configuration.
             *
             * invoke() executes the graph from START to END.
             */
            SupportState state = graph
                    .invoke(
                            GraphInput.args(input),
                            RunnableConfig.empty()
                    )
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Graph returned no final state"));


            /*
             * FINAL OUTPUT
             *
             * Read the accumulated state after graph execution.
             *
             * Each agent stores its result in a separate key.
             */
            return Map.of(
                    "customerId", customerId,
                    "message", message,
                    "customerAnalysis", state.text("customerResult"),
                    "billingAnalysis", state.text("billingResult"),
                    "policyAnalysis", state.text("policyResult"),
                    "finalResponse", state.text("finalResponse")
            );

        } catch (Exception e) {

            // Convert workflow execution failures into an application exception.
            throw new IllegalStateException(
                    "Customer support workflow failed", e);
        }
    }


    /*
     * CUSTOMER AGENT NODE
     *
     * Responsibility:
     * Understand the customer's request and identify its intent.
     *
     * Example:
     * "I was charged twice for subscription SUB1001"
     *
     * Customer Agent returns:
     * intent = DUPLICATE_PAYMENT
     *
     * The identified intent determines the next graph route.
     */
    private Map<String, Object> customerNode(SupportState state)
            throws JsonProcessingException {

        System.out.println("\n========== CUSTOMER AGENT ==========");


        // Call the independently running Customer Agent service.
        Map<String, Object> result = agents.customer(Map.of(
                "customerId", state.text("customerId"),
                "message", state.text("message")
        ));


        // Extract intent returned by Customer Agent.
        String intent =
                String.valueOf(result.getOrDefault(
                        "intent", "GENERAL_QUERY"));


        /*
         * DETERMINE NEXT ROUTE
         *
         * DUPLICATE_PAYMENT -> Billing Agent
         * PAYMENT_ISSUE     -> Billing Agent
         * POLICY_QUESTION   -> Policy Agent
         * GENERAL_QUERY     -> Final Response
         *
         * These route names must match the XML conditions.
         */
        String route = switch (intent) {

            case "DUPLICATE_PAYMENT" -> "DUPLICATE_PAYMENT";

            case "PAYMENT_ISSUE" -> "PAYMENT_ISSUE";

            case "POLICY_QUESTION" -> "POLICY_QUESTION";

            default -> "GENERAL_QUERY";
        };


        System.out.println("Identified intent: " + intent);
        System.out.println("Next route: " + route);


        /*
         * UPDATE GRAPH STATE
         *
         * route:
         * Used by LangGraph4j conditional edge selection.
         *
         * customerResult:
         * Stores the complete Customer Agent response as JSON.
         *
         * This result is available to subsequent nodes.
         */
        return Map.of(
                "route", route,
                "customerResult", mapper.writeValueAsString(result)
        );
    }


    /*
     * BILLING AGENT NODE
     *
     * Responsibility:
     * Check payment-related issues.
     *
     * Example:
     * Verify whether subscription SUB1001 was charged twice.
     *
     * Input:
     * - customerId
     * - original customer message
     * - Customer Agent result
     *
     * Output:
     * - duplicatePayment
     * - refundRequired
     * - response
     */
    private Map<String, Object> billingNode(SupportState state)
            throws JsonProcessingException {

        System.out.println("\n========== BILLING AGENT ==========");


        // Invoke Billing Agent through REST.
        Map<String, Object> result = agents.billing(Map.of(
                "customerId", state.text("customerId"),
                "message", state.text("message"),

                // Pass Customer Agent output as context.
                "customerContext", state.text("customerResult")
        ));


        /*
         * Determine whether policy evaluation is required.
         *
         * If refundRequired = true:
         *     Route to Policy Agent.
         *
         * Otherwise:
         *     Route directly to Final Response.
         */
        boolean refundRequired =
                Boolean.TRUE.equals(result.get("refundRequired"));


        String route = refundRequired
                ? "REFUND_REQUIRED"
                : "NO_REFUND_REQUIRED";


        System.out.println("Billing result: " + result);
        System.out.println("Next route: " + route);


        /*
         * UPDATE GRAPH STATE
         *
         * route:
         * Controls the next conditional edge.
         *
         * billingResult:
         * Stores the Billing Agent result.
         * Policy Agent will consume this information.
         */
        return Map.of(
                "route", route,
                "billingResult", mapper.writeValueAsString(result)
        );
    }


    /*
     * POLICY AGENT NODE
     *
     * Responsibility:
     * Evaluate refund eligibility based on billing information.
     *
     * Important:
     * Policy Agent receives Billing Agent output.
     * It should not independently assume that a duplicate payment
     * has occurred.
     *
     * Input:
     * - customerId
     * - original message
     * - Billing Agent result
     *
     * Output:
     * - duplicatePayment
     * - refundRequired
     * - refundEligible
     * - response
     */
    private Map<String, Object> policyNode(SupportState state)
            throws JsonProcessingException {

        System.out.println("\n========== POLICY AGENT ==========");


        // Invoke Policy Agent and pass Billing Agent result as context.
        Map<String, Object> result = agents.policy(Map.of(
                "customerId", state.text("customerId"),
                "message", state.text("message"),

                // This is the important inter-agent state handoff.
                "billingContext", state.text("billingResult")
        ));


        System.out.println("Policy result: " + result);


        /*
         * Policy evaluation is complete.
         *
         * POLICY_EVALUATED matches the XML transition
         * from policyAgent to finalResponse.
         */
        return Map.of(
                "route", "POLICY_EVALUATED",
                "policyResult", mapper.writeValueAsString(result)
        );
    }


    /*
     * FINAL RESPONSE NODE
     *
     * Responsibility:
     * Select the most relevant agent response.
     *
     * Priority:
     * 1. Policy Agent response, if available.
     * 2. Billing Agent response, if available.
     * 3. Customer Agent response otherwise.
     *
     * This is the final node before LangGraph4j END.
     */
    private Map<String, Object> finalNode(SupportState state) {

        System.out.println("\n========== FINAL RESPONSE ==========");

        String response;


        // If Policy Agent executed, use its response.
        if (!state.text("policyResult").isBlank()) {

            response = extractResponse(
                    state.text("policyResult"));


        // Otherwise, use Billing Agent response if available.
        } else if (!state.text("billingResult").isBlank()) {

            response = extractResponse(
                    state.text("billingResult"));


        // For general queries, use Customer Agent response.
        } else {

            response = extractResponse(
                    state.text("customerResult"));
        }


        System.out.println("Final response: " + response);


        // Save the final response in graph state.
        return Map.of("finalResponse", response);
    }


    /*
     * EXTRACT RESPONSE
     *
     * Agent responses are stored as JSON strings.
     *
     * Example:
     * {
     *   "agent": "billing-agent",
     *   "duplicatePayment": true,
     *   "response": "A possible duplicate payment was identified."
     * }
     *
     * This method extracts only the "response" field.
     */
    private String extractResponse(String json) {

        try {

            // Convert JSON string into a Java Map.
            Map<?, ?> value = mapper.readValue(json, Map.class);


            // Extract customer-facing response.
            Object response = value.get("response");


            // Provide a fallback if response is missing.
            return response == null
                    ? "Your request has been analyzed by customer support."
                    : String.valueOf(response);

        } catch (Exception e) {

            // Fallback for invalid JSON.
            return "Your request has been analyzed. " + json;
        }
    }
}
