package com.acme.support.mcp;

import org.springframework.stereotype.Component;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@Component
public class SupportTools {
	private final RestClient ml;

	public SupportTools(RestClient.Builder builder, @Value("${ml.service.url:http://localhost:8090}") String mlUrl) {
		this.ml = builder.baseUrl(mlUrl).build();
	}

	@McpTool(description = "Look up a demo support ticket by ticket identifier")
	public String lookupTicket(String ticketId) {
		return "Ticket " + ticketId + ": PAYMENT_DUPLICATE; customer reports two charges; status OPEN; priority HIGH";
	}

	@McpTool(description = "Create a support escalation note for a ticket")
	public String escalateTicket(String ticketId, String reason) {
		return "Escalation recorded for " + ticketId + "; reason=" + reason;
	}

	@McpTool(description = "Return current service status for a named service")
	public String serviceStatus(String serviceName) {
		return serviceName + " status=OPERATIONAL (demo data)";
	}

	@McpTool(description = "Predict support ticket priority using the supervised ML classifier. Supply subject, body, plan tier, account age in days, and prior ticket count in the last 30 days.")
	public Map predictTicketPriority(String subject, String body, String planTier, int accountAgeDays,
			int priorTicketCount30d) {
		return ml
				.post().uri("/api/ml/predict-priority").body(Map.of("subject", subject, "body", body, "planTier",
						planTier, "accountAgeDays", accountAgeDays, "priorTicketCount30d", priorTicketCount30d))
				.retrieve().body(Map.class);
	}
}
