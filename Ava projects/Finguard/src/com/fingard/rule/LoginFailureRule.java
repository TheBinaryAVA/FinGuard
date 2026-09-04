package com.fingard.rule;

import com.fingard.model.Event;
import com.fingard.model.LoginEvent;
import com.fingard.model.RiskResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class LoginFailureRule extends ATORule {
    private final Duration window;
    private final int failureThreshold;
    private final Map<String, Deque<Instant>> failedLogins = new HashMap<>();

    public LoginFailureRule(Duration window, int failureThreshold) {
        super("ATO-LOGIN-FAILURE", "Login Failure Burst");
        if (window == null || window.isZero() || window.isNegative() || failureThreshold < 1) {
            throw new IllegalArgumentException("Login failure configuration is invalid.");
        }
        this.window = window;
        this.failureThreshold = failureThreshold;
    }

    public LoginFailureRule() {
        this(Duration.ofMinutes(10), 5);
    }

    @Override
    public synchronized RiskResult evaluate(Event event) {
        if (!(event instanceof LoginEvent)) {
            return pass();
        }
        LoginEvent loginEvent = (LoginEvent) event;
        if (loginEvent.isSuccess()) {
            cleanupExpired(Instant.now());
            return pass();
        }
        cleanupExpired(Instant.now());
        Deque<Instant> failures = failedLogins.computeIfAbsent(loginEvent.getAccountId(), key -> new ArrayDeque<>());
        Instant cutoff = loginEvent.getTimestamp().minus(window);
        failures.removeIf(timestamp -> timestamp.isBefore(cutoff));
        failures.addLast(loginEvent.getTimestamp());
        while (failures.size() > failureThreshold + 1) {
            failures.removeFirst();
        }
        if (failures.size() >= failureThreshold) {
            return trigger(20, failures.size() + " failed logins occurred within " + window + ".");
        }
        return pass();
    }

    private void cleanupExpired(Instant referenceTime) {
        Instant cutoff = referenceTime.minus(window);
        failedLogins.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(timestamp -> timestamp.isBefore(cutoff));
            return entry.getValue().isEmpty();
        });
        while (failedLogins.size() > 10_000) {
            failedLogins.remove(failedLogins.keySet().iterator().next());
        }
    }
}