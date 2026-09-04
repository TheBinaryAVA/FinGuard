package com.fingard.rule;
import com.fingard.model.Event;
import com.fingard.model.RiskLevel;
import com.fingard.model.RiskResult;

public abstract class FraudRule implements DetectionRule {

    private final String ruleId;
    private final String ruleName;
    private boolean enabled;

    public FraudRule(String ruleId, String ruleName) {
        this(ruleId, ruleName, true);
    }

    public FraudRule(String ruleId, String ruleName, boolean enabled) {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("Rule ID cannot be blank.");
        }
        if (ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("Rule name cannot be blank.");
        }
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.enabled = enabled;
    }

    @Override
    public final String getRuleId() {
        return this.ruleId;
    }

    @Override
    public final String getRuleName() {
        return this.ruleName;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Helper method to return a default LOW risk outcome when no conditions are met.
     */
    protected RiskResult pass() {
        return new RiskResult(0, RiskLevel.LOW, com.fingard.model.Decision.ALLOW, null,
                java.util.Collections.emptyList(),
                java.util.Collections.singletonList("Rule [" + ruleName + "] passed: No suspicious pattern detected."));
    }

    protected RiskResult trigger(int score, String reason) {
        return new RiskResult(score, RiskLevel.LOW, com.fingard.model.Decision.REVIEW, null,
                java.util.Collections.singletonList(ruleName), java.util.Collections.singletonList(reason));
    }

    @Override
    public abstract RiskResult evaluate(Event event);
}