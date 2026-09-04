package com.fingard.model;

import java.io.Serializable;
import java.time.Instant;

public class Alert implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String alertId;
    private final String accountId;
    private final String eventId;
    private final Instant timestamp;
    private final RiskResult riskResult;

    public Alert(String alertId, String accountId, String eventId, Instant timestamp, RiskResult riskResult) {
        if (alertId == null || alertId.isBlank() || accountId == null || accountId.isBlank()
                || eventId == null || eventId.isBlank() || riskResult == null) {
            throw new IllegalArgumentException("Alert identity and risk result are required.");
        }
        this.alertId = alertId;
        this.accountId = accountId;
        this.eventId = eventId;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.riskResult = riskResult;
    }

    public String getAlertId() { return alertId; }
    public String getAccountId() { return accountId; }
    public String getEventId() { return eventId; }
    public Instant getTimestamp() { return timestamp; }
    public RiskResult getRiskResult() { return riskResult; }
}