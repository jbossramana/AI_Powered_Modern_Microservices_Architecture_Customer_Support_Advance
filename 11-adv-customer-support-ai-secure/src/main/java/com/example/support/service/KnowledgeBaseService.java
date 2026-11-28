package com.example.support.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService implements ApplicationRunner {
    private final VectorStore vectorStore;
    private final SecurityGuard guard;
    private final AuditService audit;
    private final Map<String, Document> pending = new ConcurrentHashMap<>();

    public KnowledgeBaseService(VectorStore vectorStore, SecurityGuard guard, AuditService audit) {
        this.vectorStore = vectorStore; this.guard = guard; this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        addTrusted("shipping", "Shipping policy", "Standard delivery normally takes 3 to 5 business days. Express delivery normally takes 1 to 2 business days. Customers can check shipment status using the order tracking page.");
        addTrusted("refunds", "Refund policy", "Eligible refunds are reviewed by the support team. After approval, the refund is normally processed to the original payment method within 5 to 7 business days. Never request or store a full payment-card number.");
        addTrusted("returns", "Return policy", "A return request can normally be raised within 30 days of delivery for eligible products. The product should be unused and returned with its original packaging. Some product categories may have different conditions.");
        addTrusted("account", "Account access", "For account access problems, use the password reset option on the sign-in page. Support agents will never ask customers to share their password or one-time password.");
    }

    private void addTrusted(String id, String title, String content) {
        Document doc = Document.builder().id(id).text(title + ". " + content)
            .metadata(Map.of("tenant", "public", "approved", true, "source", "curated-faq", "title", title)).build();
        vectorStore.add(List.of(doc));
    }

    public List<Document> search(String query) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(3)
            .similarityThreshold(0.35)
            .filterExpression("tenant == 'public' && approved == true")
            .build());
    }

    public String submitForReview(String title, String content, String source, String actor) {
        if (title.length() > 100 || content.length() > 1200 || source.length() > 80)
            throw new IllegalArgumentException("Title, content or source exceeds its allowed size.");
        if (guard.looksLikeInjection(content)) throw new SecurityException("Knowledge document contains instruction-like content and was rejected.");
        String id = "pending-" + UUID.randomUUID();
        Document doc = Document.builder().id(id).text(title + ". " + content)
            .metadata(Map.of("tenant", "public", "approved", false, "source", source, "title", title)).build();
        pending.put(id, doc);
        audit.record(actor, "KNOWLEDGE_INGEST", "PENDING_REVIEW", id);
        return id;
    }

    public void approve(String id, String actor) {
        Document doc = pending.remove(id);
        if (doc == null) throw new IllegalArgumentException("Pending document not found.");
        vectorStore.add(List.of(Document.builder().id(doc.getId()).text(doc.getText())
            .metadata(Map.of("tenant", "public", "approved", true,
                "source", doc.getMetadata().getOrDefault("source", "admin-reviewed"),
                "title", doc.getMetadata().getOrDefault("title", "Reviewed document"))).build()));
        audit.record(actor, "KNOWLEDGE_APPROVE", "APPROVED", id);
    }

    public int pendingCount() { return pending.size(); }
}
