
package com.example.support.knowledge;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * KnowledgeBase
 *
 * A simple in-memory knowledge retriever for demonstrating RAG.
 *
 * This implementation uses keyword matching instead of embeddings.
 *
 * In a production RAG application, replace this implementation
 * with Spring AI VectorStore and embedding-based similarity search.
 */
@Component
public class KnowledgeBase {

    /**
     * Article represents one knowledge-base document.
     *
     * id    : Unique document identifier.
     * title : Document title.
     * text  : Actual knowledge content.
     */
    public record Article(String id, String title, String text) {}

    /**
     * In-memory knowledge base containing sample support articles.
     *
     * List.of() creates an immutable list.
     *
     * These documents act as the knowledge source for RAG retrieval.
     */
    private final List<Article> articles = List.of(

        new Article(
            "KB-101",
            "Refund policy",
            "Refund eligibility depends on the product and payment status. " +
            "A duplicate payment should be reviewed by billing support. " +
            "This assistant can explain the policy but cannot issue refunds."
        ),

        new Article(
            "KB-102",
            "Ticket status",
            "Customers can ask support for ticket status using the ticket ID. " +
            "Example ticket TCK-1001 is currently OPEN and assigned to the " +
            "Application Support team. Expected first response: within 4 business hours."
        ),

        new Article(
            "KB-103",
            "Login troubleshooting",
            "If login fails, verify the registered email, reset the password, " +
            "and check the service status page. Never share passwords or " +
            "one-time passcodes with support."
        ),

        new Article(
            "KB-104",
            "Escalation policy",
            "Production outages affecting multiple regions are high priority. " +
            "Provide the incident ID, affected regions, start time, and business impact. " +
            "The on-call team should be paged by an authorized operator."
        ),

        new Article(
            "KB-105",
            "Privacy policy",
            "Do not request or expose full payment card numbers, passwords, " +
            "authentication codes, or unnecessary personal data. " +
            "Verify customer identity through approved authenticated channels."
        )
    );

    /**
     * Searches the knowledge base for documents relevant to a question.
     *
     * @param question Customer's question.
     * @param limit    Maximum number of matching articles to return.
     *
     * @return List of relevant articles ordered by relevance score.
     */
    public List<Article> search(String question, int limit) {

        /*
         * STEP 1: Convert the question into searchable terms.
         *
         * toLowerCase():
         * Makes searching case-insensitive.
         *
         * split("[^a-z0-9-]+"):
         * Splits the question wherever a character other than
         * letters, digits, or hyphens occurs.
         *
         * filter():
         * Removes words having two or fewer characters.
         *
         * Collectors.toSet():
         * Removes duplicate words.
         *
         * Example:
         * "How does the refund policy work?"
         *
         * Result:
         * ["how", "does", "the", "refund", "policy", "work"]
         * After filtering:
         * ["does", "the", "refund", "policy", "work"]
         */
        Set<String> terms = Arrays.stream(
                    question.toLowerCase(Locale.ROOT)
                            .split("[^a-z0-9-]+")
                )
                .filter(t -> t.length() > 2)
                .collect(Collectors.toSet());

        /*
         * STEP 2: Calculate a relevance score for every article.
         *
         * articles.stream():
         * Processes all knowledge-base articles.
         *
         * map():
         * Creates a Map.Entry containing:
         *   Key   = Article
         *   Value = Relevance score
         *
         * score():
         * Counts how many question terms appear in the article.
         */
        return articles.stream()

                .map(article ->
                        Map.entry(article, score(article, terms))
                )

                /*
                 * STEP 3: Remove articles with zero relevance.
                 *
                 * If none of the question terms occur in an article,
                 * its relevance score is zero.
                 */
                .filter(entry -> entry.getValue() > 0)

                /*
                 * STEP 4: Sort articles by relevance score.
                 *
                 * Higher scores appear first.
                 *
                 * Example:
                 * KB-101 -> Score 3
                 * KB-105 -> Score 1
                 */
                .sorted((a, b) ->
                        Integer.compare(
                                b.getValue(),
                                a.getValue()
                        )
                )

                /*
                 * STEP 5: Return only the requested number of articles.
                 *
                 * Example:
                 * limit = 3 means return at most 3 articles.
                 */
                .limit(limit)

                /*
                 * STEP 6: Extract Article objects from Map.Entry.
                 *
                 * The relevance score is used internally for ranking.
                 * The caller receives only the matching articles.
                 */
                .map(Map.Entry::getKey)

                /*
                 * Convert the stream into an immutable List.
                 */
                .toList();
    }

    /**
     * Calculates the relevance score of an article.
     *
     * This is a simple keyword-overlap scoring algorithm.
     *
     * @param article Knowledge-base article.
     * @param terms   Keywords extracted from customer question.
     *
     * @return Number of matching keywords.
     */
    private int score(Article article, Set<String> terms) {

        /*
         * Combine title and article content into one searchable string.
         *
         * Searching both title and text improves keyword matching.
         */
        String corpus = (
                article.title() + " " + article.text()
        ).toLowerCase(Locale.ROOT);

        // Initially, no keywords have matched.
        int score = 0;

        /*
         * Compare each question keyword with the article content.
         *
         * contains():
         * Checks whether the article contains the keyword.
         *
         * Each matching keyword increases the score by one.
         */
        for (String term : terms) {

            if (corpus.contains(term)) {
                score++;
            }
        }

        // Return the article's relevance score.
        return score;
    }
}
