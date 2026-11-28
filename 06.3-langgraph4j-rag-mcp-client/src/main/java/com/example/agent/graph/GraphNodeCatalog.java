package com.example.agent.graph;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GraphNodeCatalog {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final SyncMcpToolCallbackProvider mcpTools;

    public GraphNodeCatalog(VectorStore vectorStore,
                            ChatClient.Builder chatClientBuilder,
                            SyncMcpToolCallbackProvider mcpTools) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.mcpTools = mcpTools;
    }

    public NodeAction<SupportState> ragNode() {
        return state -> {
            List<Document> documents = vectorStore.similaritySearch(state.str("question"));
            String context = documents == null ? "" : documents.stream()
                    .limit(3)
                    .map(Document::getText)
                    .reduce("", (left, right) -> left + "\n---\n" + right);
            return Map.of("knowledge", context);
        };
    }

    /** The model is given the MCP tool callbacks discovered from the remote MCP server. */
    public NodeAction<SupportState> mcpNode() {
        return state -> {
            String result = chatClient.prompt()
                    .system("""
                            You are an order investigation assistant.
                            You MUST call the MCP tool get_order_status exactly once.
                            Use the supplied customerId and orderId.
                            Do not guess or fabricate order data.
                            After the tool returns, summarize its result as compact JSON.
                            """)
                    .user("customerId=" + state.str("customerId")
                            + ", orderId=" + state.str("orderId"))
                    .tools(mcpTools)
                    .call()
                    .content();
            return Map.of("orderDetails", Objects.toString(result, "MCP returned no content"));
        };
    }

    public NodeAction<SupportState> decisionNode() {
        return state -> {
            String modelDecision = chatClient.prompt()
                    .system("""
                            Classify this support case using ONLY the supplied policy and order evidence.
                            Return exactly one label:
                            REFUND_REVIEW - duplicate payment or refund request needs human review.
                            INFORMATION_ONLY - no refund or financial action is requested.
                            Never approve or execute a refund.
                            """)
                    .user("""
                            Customer question:
                            %s

                            Retrieved policy:
                            %s

                            MCP order result:
                            %s
                            """.formatted(state.str("question"), state.str("knowledge"), state.str("orderDetails")))
                    .call()
                    .content();

            String normalized = modelDecision != null
                    && modelDecision.toUpperCase().contains("INFORMATION_ONLY")
                    ? "INFORMATION_ONLY" : "REFUND_REVIEW"; // fail closed on ambiguous output
            return Map.of("decision", normalized);
        };
    }

    public NodeAction<SupportState> approvalNode() {
        return state -> {
            if ("REFUND_REVIEW".equals(state.str("decision"))) {
                return Map.of(
                        "approvalStatus", "PENDING",
                        "approvalComment", "Waiting for supervisor review. No refund has been executed."
                );
            }
            return Map.of(
                    "approvalStatus", "NOT_REQUIRED",
                    "approvalComment", "Information-only request; no financial action is proposed."
            );
        };
    }

    public NodeAction<SupportState> responseNode() {
        return state -> Map.of("answer",
                "Decision: " + state.str("decision")
                        + "\nOrder details: " + state.str("orderDetails")
                        + "\nPolicy context: " + state.str("knowledge")
                        + "\nApproval status: " + state.str("approvalStatus")
                        + "\n" + state.str("approvalComment"));
    }
}
