package com.fingard.engine;

import com.fingard.model.Decision;
import com.fingard.model.Event;
import com.fingard.model.RiskLevel;
import com.fingard.model.RiskResult;
import java.util.ArrayList;
import java.util.List;

public class RiskEngine {
    private final int reviewThreshold;
    private final int blockThreshold;

    public RiskEngine(int reviewThreshold, int blockThreshold) {
        if (reviewThreshold < 0 || blockThreshold <= reviewThreshold || blockThreshold > 100) {
            throw new IllegalArgumentException("Risk thresholds are invalid.");
        }
        this.reviewThreshold = reviewThreshold;
        this.blockThreshold = blockThreshold;
    }

    public RiskEngine() {
        this(30, 75);
    }

    public RiskResult assess(Event event, List<RiskResult> ruleResults,
                             EventCorrelator.CorrelationSignal signal) {
        int score = 0;
        List<String> triggeredRules = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (RiskResult result : ruleResults) {
            score += result.getScore();
            triggeredRules.addAll(result.getTriggeredRules());
            if (result.getScore() > 0) {
                reasons.addAll(result.getReasons());
            }
        }
        if (signal.getFailedLoginCount() >= 5) {
            score += 10;
            triggeredRules.add("Historical Login Failure Context");
            reasons.add(signal.getFailedLoginCount() + " failed logins are active in the correlation window.");
        }
        if (signal.hasRecentDevice() && signal.hasRecentBeneficiary()) {
            score += 20;
            triggeredRules.add("Recent Device and Beneficiary Context");
            reasons.add("A new device and beneficiary change are both active in the account window.");
        }
        if (signal.hasSuspiciousSequence()) {
            score += 20;
            triggeredRules.add("Account Takeover Sequence");
            reasons.add("A new device, beneficiary addition, and transaction formed a suspicious sequence.");
        }
        score = Math.min(100, score);
        RiskLevel level = score >= blockThreshold ? RiskLevel.CRITICAL
                : score >= reviewThreshold ? RiskLevel.HIGH
                : score >= 25 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        Decision decision = score >= blockThreshold ? Decision.BLOCK
                : score >= reviewThreshold ? Decision.REVIEW : Decision.ALLOW;
        return new RiskResult(score, level, decision, event.getEventId(), triggeredRules, reasons);
    }
}