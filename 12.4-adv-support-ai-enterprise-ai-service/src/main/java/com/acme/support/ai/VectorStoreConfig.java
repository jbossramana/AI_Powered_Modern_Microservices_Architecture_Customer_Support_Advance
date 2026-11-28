package com.acme.support.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {
    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // SimpleVectorStore keeps vectors in this JVM's memory; all data is lost on restart.
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
