package com.example.support.guardrail;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Response-side advisor for synchronous ChatClient calls.
 *
 * It lets the downstream chain (including the model and tool-calling loop)
 * execute first, then inspects the final assistant text while the response
 * travels back through the advisor chain.
 *
 * Tool-call generations are deliberately left unchanged. The advisor applies
 * the output policy to assistant text, not to the model's tool-call arguments.
 */
@Component
public class OutputGuardrailAdvisor implements CallAdvisor {

    private final OutputGuardrail outputGuardrail;

    public OutputGuardrailAdvisor(OutputGuardrail outputGuardrail) {
        this.outputGuardrail = outputGuardrail;
    }

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest request,
            CallAdvisorChain chain) {

        // Continue first. The model response comes back after this call returns.
        ChatClientResponse response = chain.nextCall(request);

        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResults().isEmpty()) {
            return response;
        }

        List<Generation> sanitizedGenerations = chatResponse.getResults().stream()
                .map(this::sanitizeGeneration)
                .toList();

        ChatResponse sanitizedChatResponse = ChatResponse.builder()
                .from(chatResponse) // preserve model metadata and token usage
                .generations(sanitizedGenerations)
                .build();

        return response.mutate()
                .chatResponse(sanitizedChatResponse)
                .build();
    }

    private Generation sanitizeGeneration(Generation generation) {
        var assistantMessage = generation.getOutput();

        // Tool-call messages are part of the tool execution protocol. Do not
        // redact or rewrite their arguments here.
        if (assistantMessage == null || assistantMessage.hasToolCalls()
                || assistantMessage.getText() == null) {
            return generation;
        }

        String sanitizedText = outputGuardrail.sanitize(assistantMessage.getText());

        if (Objects.equals(sanitizedText, assistantMessage.getText())) {
            return generation;
        }

        var sanitizedMessage = assistantMessage.mutate()
                .content(sanitizedText)
                .build();

        return new Generation(sanitizedMessage, generation.getMetadata());
    }

    @Override
    public String getName() {
        return "OutputGuardrailAdvisor";
    }

    @Override
    public int getOrder() {
        // Lowest priority means last on the request path and first on the
        // response path, so the generated text is inspected before returning.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
