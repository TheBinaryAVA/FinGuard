package com.fingard.rule;

import com.fingard.model.Decision;
import com.fingard.model.Event;
import com.fingard.model.RiskLevel;
import com.fingard.model.RiskResult;

public abstract class ATORule implements DetectionRule {
    private final String ruleId;
    private final String ruleName;
    private volatile boolean enabled;

    protected ATORule(String ruleId, String ruleName) {
        this(ruleId, ruleName, true);
    }

    protected ATORule(String ruleId, String ruleName, boolean enabled) {
        if (ruleId == null || ruleId.isBlank() || ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("Rule ID and name cannot be blank.");
        }
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.enabled = enabled;
    }

    @Override
    public final String getRuleId() {
        return ruleId;
    }

    @Override
    public final String getRuleName() {
        return ruleName;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected RiskResult pass() {
        return new RiskResult(0, RiskLevel.LOW, Decision.ALLOW, null,
                java.util.Collections.emptyList(),
                java.util.Collections.singletonList("Rule [" + ruleName + "] passed: No suspicious pattern detected."));
    }

    protected RiskResult trigger(int score, String reason) {
        return new RiskResult(score, RiskLevel.LOW, Decision.REVIEW, null,
                java.util.Collections.singletonList(ruleName), java.util.Collections.singletonList(reason));
    }

    @Override
    public abstract RiskResult evaluate(Event event);
}