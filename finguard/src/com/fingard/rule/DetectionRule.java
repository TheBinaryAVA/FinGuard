package com.fingard.rule;

import com.fingard.model.Event;
import com.fingard.model.RiskResult;

public interface DetectionRule {
    RiskResult evaluate(Event event);
    String getRuleId();
    String getRuleName();

    default boolean isEnabled() {
        return true;
    }
}