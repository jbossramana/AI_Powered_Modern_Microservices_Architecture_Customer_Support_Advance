package com.example.agent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import java.util.List;

@Configuration
public class RagConfig {
    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
    @Bean
    ApplicationRunner seedPolicy(VectorStore store) {
        return args -> store.add(List.of(
            new Document("Refund policy: A duplicate payment can be refunded after transaction verification. Refunds above INR 1000 require supervisor approval."),
            new Document("Order support policy: Verify the customer and order before disclosing order status. Never expose full payment card numbers, passwords, or authentication tokens."),
            new Document("Cancellation policy: An order not yet shipped may be cancelled. A shipped order must follow the return process.")
        ));
    }
}
