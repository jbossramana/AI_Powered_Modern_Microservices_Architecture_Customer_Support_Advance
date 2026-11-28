package com.example.orchestrator.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AgentRestClient {

    private final RestClient restClient;
    private final String customerUrl;
    private final String billingUrl;
    private final String policyUrl;

    public AgentRestClient(
            RestClient.Builder builder,
            @Value("${agents.customer-url}") String customerUrl,
            @Value("${agents.billing-url}") String billingUrl,
            @Value("${agents.policy-url}") String policyUrl) {
        this.restClient = builder.build();
        this.customerUrl = customerUrl;
        this.billingUrl = billingUrl;
        this.policyUrl = policyUrl;
    }

    public Map<String, Object> customer(Map<String, Object> body) {
        return post(customerUrl, body);
    }

    public Map<String, Object> billing(Map<String, Object> body) {
        return post(billingUrl, body);
    }

    public Map<String, Object> policy(Map<String, Object> body) {
        return post(policyUrl, body);
    }

    private Map<String, Object> post(String baseUrl, Map<String, Object> body) {
        return restClient.post()
                .uri(baseUrl + "/api/agent/analyze")
                .body(body)
                .retrieve()
                .body(Map.class);
    }
}
