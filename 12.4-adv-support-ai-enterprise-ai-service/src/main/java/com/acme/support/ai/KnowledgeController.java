package com.acme.support.ai;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
	private final VectorStore vectorStore;
	private final ChatClient chatClient;

	public KnowledgeController(VectorStore vectorStore, ChatClient.Builder builder) {
		this.vectorStore = vectorStore;
		this.chatClient = builder.build();
	}

	@PostMapping("/ingest")
	public Map<String, Object> ingest(@RequestBody IngestRequest r) {
		List<Document> docs = List
				.of(new Document(r.text(), Map.of("source", r.source() == null ? "inline" : r.source())));
		vectorStore.add(docs);
		return Map.of("status", "INDEXED", "documents", docs.size());
	}

	@PostMapping("/ask")
	public Map<String, String> ask(@RequestBody AskRequest r) {
		String answer = chatClient.prompt().system(
				"Answer only from retrieved support knowledge. If evidence is missing, say so. Do not invent policy.")
				.user(r.question()).advisors(QuestionAnswerAdvisor.builder(vectorStore).build()).call().content();
		return Map.of("answer", answer);
	}

	public record IngestRequest(String text, String source) {
	}

	public record AskRequest(String question) {
	}
}
