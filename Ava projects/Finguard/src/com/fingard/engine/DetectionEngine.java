package com.fingard.engine;

import com.fingard.exception.DetectionException;
import com.fingard.exception.InvalidEventException;
import com.fingard.model.Decision;
import com.fingard.model.Event;
import com.fingard.model.RiskLevel;
import com.fingard.model.RiskResult;
import com.fingard.rule.DetectionRule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DetectionEngine {
    private final List<DetectionRule> rules;
    private final EventCorrelator correlator;
    private final RiskEngine riskEngine;

    public DetectionEngine(List<DetectionRule> rules, EventCorrelator correlator, RiskEngine riskEngine) {
        if (rules == null || correlator == null || riskEngine == null) {
            throw new IllegalArgumentException("Detection engine dependencies are required.");
        }
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        this.correlator = correlator;
        this.riskEngine = riskEngine;
    }

    public RiskResult detect(Event event) {
        if (event == null) {
            throw new InvalidEventException("Event cannot be null.");
        }
        EventCorrelator.CorrelationSignal signal = correlator.record(event);
        if (signal.isDuplicate()) {
            return new RiskResult(0, RiskLevel.LOW, Decision.ALLOW, event.getEventId(),
                Collections.emptyList(), Collections.singletonList("Duplicate event ignored."));
        }
        List<RiskResult> results = new ArrayList<>();
        for (DetectionRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            try {
                results.add(rule.evaluate(event));
            } catch (RuntimeException exception) {
                throw new DetectionException("Rule evaluation failed for " + rule.getRuleId(), exception);
            }
        }
        return riskEngine.assess(event, results, signal);
    }

    public List<DetectionRule> getRules() {
        return rules;
    }
}