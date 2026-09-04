package com.fingard.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RiskResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final RiskLevel riskLevel;
    private final String reason;
    private final int score;
    private final Decision decision;
    private final String eventId;
    private final ArrayList<String> triggeredRules;
    private final ArrayList<String> reasons;

    public RiskResult(RiskLevel riskLevel, String reason) {
        this(0, riskLevel, Decision.ALLOW, null, Collections.emptyList(),
                reason == null ? Collections.emptyList() : Collections.singletonList(reason));
    }

    public RiskResult(int score, RiskLevel riskLevel, Decision decision, String eventId,
                      List<String> triggeredRules, List<String> reasons) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100.");
        }
        if (riskLevel == null || decision == null) {
            throw new IllegalArgumentException("Risk level and decision are required.");
        }
        this.score = score;
        this.riskLevel = riskLevel;
        this.decision = decision;
        this.eventId = eventId;
        this.triggeredRules = immutableCopy(triggeredRules);
        this.reasons = immutableCopy(reasons);
        this.reason = this.reasons.isEmpty() ? "No suspicious activity detected." : this.reasons.get(0);
    }

    private static ArrayList<String> immutableCopy(List<String> values) {
        return new ArrayList<>(values == null ? Collections.emptyList() : values);
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getReason() {
        return reason;
    }

    public int getScore() {
        return score;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getEventId() {
        return eventId;
    }

    public List<String> getTriggeredRules() {
        return Collections.unmodifiableList(triggeredRules);
    }

    public List<String> getReasons() {
        return Collections.unmodifiableList(reasons);
    }

    @Override
    public String toString() {
        return "RiskResult{score=" + score + ", riskLevel=" + riskLevel
            + ", decision=" + decision + ", triggeredRules=" + triggeredRules
            + ", reasons=" + reasons + "}";
    }
}