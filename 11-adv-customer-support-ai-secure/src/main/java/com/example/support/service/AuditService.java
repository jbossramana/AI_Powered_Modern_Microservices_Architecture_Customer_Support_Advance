package com.example.support.service;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(String actor, String event, String outcome, String details) {
        String safeDetails = details == null ? "" : details.replaceAll("[\\r\\n]", " ");
        if (safeDetails.length() > 490) safeDetails = safeDetails.substring(0, 490);
        jdbc.update("INSERT INTO security_audit(event_time, actor, event_type, outcome, details) VALUES (?, ?, ?, ?, ?)",
            LocalDateTime.now(), actor, event, outcome, safeDetails);
    }
}
