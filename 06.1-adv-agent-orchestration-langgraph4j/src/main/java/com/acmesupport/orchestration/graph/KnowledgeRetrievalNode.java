
package com.acmesupport.orchestration.graph;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Knowledge Retrieval Node
 *
 * Responsibility:
 * 1. Read the customer's ticket from the shared graph state.
 * 2. Search the available knowledge-base articles.
 * 3. Identify the article having the highest keyword overlap.
 * 4. Store the selected article in the graph state.
 *
 * IMPORTANT:
 * This is a simplified keyword-based retrieval implementation.
 * It does NOT use embeddings, VectorStore, or an LLM.
 *
 * In a production application, this retrieval logic can be
 * replaced with Spring AI VectorStore semantic search.
 *
 * The remaining graph structure, state shape, and edges
 * do not need to change.
 */
@Component
public class KnowledgeRetrievalNode
        implements NodeAction<SupportGraphState> {

    /**
     * Represents one knowledge-base article.
     *
     * Each article contains:
     * - title: Article heading.
     * - content: Actual knowledge-base information.
     *
     * Java record automatically generates constructor,
     * accessor methods, equals(), hashCode(), and toString().
     */
    private record Article(String title, String content) {
    }

    /**
     * Hardcoded knowledge-base articles.
     *
     * In a real application, these articles may come from:
     * - Database
     * - PDF documents
     * - External knowledge-base system
     * - Spring AI VectorStore
     */
    private final List<Article> articles = List.of(

            // Article 1: Password reset information
            new Article(
                    "Password resets",
                    "Customers can reset a forgotten password from the sign-in page by clicking "
                            + "'Forgot password?' and following the emailed link, valid for 30 minutes. "
                            + "Free accounts are limited to 3 resets per day."
            ),

            // Article 2: Shared folder access problems
            new Article(
                    "Shared folder access",
                    "If a customer can't access a shared folder, check: the invite may have gone to a "
                            + "different email address than they signed in with; the folder owner's account "
                            + "may be over its storage limit and read-only; a workspace admin may have "
                            + "restricted external sharing."
            ),

            // Article 3: Billing and refund policies
            new Article(
                    "Billing and refunds",
                    "Subscriptions are billed monthly or annually. Refunds are automatic and full within "
                            + "14 days of subscribing, or any time for duplicate/erroneous charges - those "
                            + "should be escalated to a human to verify against payment records."
            ),

            // Article 4: Storage limits and account restrictions
            new Article(
                    "Storage limits",
                    "Free plans include 5 GB, Pro includes 2 TB, Business includes 10 TB per seat. Accounts "
                            + "over their limit become read-only for new uploads but files are never deleted."
            )
    );

    /**
     * Main entry point of this LangGraph4j node.
     *
     * LangGraph4j invokes this method when the graph
     * execution reaches KnowledgeRetrievalNode.
     *
     * @param state Current shared graph state containing ticket details.
     *
     * @return Map containing the retrieved knowledge-base context.
     */
    @Override
    public Map<String, Object> apply(SupportGraphState state) {

        // Step 1: Read the customer's ticket from shared graph state.
        // Convert it to lowercase to make matching case-insensitive.
        String ticket = state.ticketText().toLowerCase();

        // Step 2: Search all available knowledge-base articles.
        String bestMatch = articles.stream()

                // Step 3: Calculate the keyword overlap score
                // between the customer ticket and each article.
                //
                // Comparator.comparingInt() compares articles
                // using their calculated integer scores.
                //
                // max() selects the article with the highest score.
                .max(Comparator.comparingInt(
                        article -> overlapScore(
                                ticket,
                                article.content().toLowerCase()
                        )
                ))

                // Step 4: Convert the selected article into a
                // formatted text block containing its title and content.
                //
                // Optional.map() transforms the selected Article
                // into a String.
                .map(article ->
                        "## " + article.title()
                                + "\n" + article.content()
                )

                // Step 5: If no articles are available,
                // return a default message.
                .orElse(
                        "No closely matching knowledge-base article was found."
                );

        // Step 6: Return the retrieved context as a graph-state update.
        //
        // RETRIEVED_CONTEXT is the key in SupportGraphState.
        // bestMatch is the selected knowledge-base article.
        //
        // LangGraph4j uses this Map to update the shared graph state.
        return Map.of(
                SupportGraphState.RETRIEVED_CONTEXT,
                bestMatch
        );
    }

    /**
     * Calculates the keyword overlap between the customer ticket
     * and a knowledge-base article.
     *
     * Algorithm:
     * 1. Split the ticket into individual words.
     * 2. Ignore words having 3 or fewer characters.
     * 3. Check whether each remaining word exists in the article.
     * 4. Count the matching words.
     *
     * The resulting count represents the matching score.
     *
     * @param ticketText  Customer's ticket text.
     * @param articleText Knowledge-base article content.
     * @return Number of matching words.
     */
    private int overlapScore(String ticketText, String articleText) {

        return (int) Arrays.stream(

                // Split the customer ticket into individual words.
                // \\W+ represents one or more non-word characters.
                ticketText.split("\\W+")
        )

                // Consider only words having more than 3 characters
                // and present in the knowledge-base article.
                .filter(word ->
                        word.length() > 3
                                && articleText.contains(word)
                )

                // Count the matching words.
                // count() returns long, so convert it to int.
                .count();
    }
}
