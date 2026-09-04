package com.fingard.rule;

import com.fingard.model.Event;
import com.fingard.model.RiskResult;
import com.fingard.model.Transaction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class VelocityRule extends FraudRule {
    private final Duration window;
    private final int maximumTransactions;
    private final Map<String, Deque<Instant>> transactionTimes = new HashMap<>();

    public VelocityRule(Duration window, int maximumTransactions) {
        super("FRAUD-VELOCITY", "Transaction Velocity");
        if (window == null || window.isZero() || window.isNegative() || maximumTransactions < 1) {
            throw new IllegalArgumentException("Velocity configuration is invalid.");
        }
        this.window = window;
        this.maximumTransactions = maximumTransactions;
    }

    public VelocityRule() {
        this(Duration.ofMinutes(10), 3);
    }

    @Override
    public synchronized RiskResult evaluate(Event event) {
        if (!(event instanceof Transaction)) {
            return pass();
        }
        Transaction transaction = (Transaction) event;
        cleanupExpired(Instant.now());
        Deque<Instant> timestamps = transactionTimes.computeIfAbsent(transaction.getAccountId(), key -> new ArrayDeque<>());
        Instant cutoff = transaction.getTimestamp().minus(window);
        timestamps.removeIf(timestamp -> timestamp.isBefore(cutoff));
        timestamps.addLast(transaction.getTimestamp());
        while (timestamps.size() > maximumTransactions + 1) {
            timestamps.removeFirst();
        }
        if (timestamps.size() > maximumTransactions) {
            return trigger(25, "More than " + maximumTransactions + " transactions occurred within " + window + ".");
        }
        return pass();
    }

    private void cleanupExpired(Instant referenceTime) {
        Instant cutoff = referenceTime.minus(window);
        transactionTimes.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(timestamp -> timestamp.isBefore(cutoff));
            return entry.getValue().isEmpty();
        });
        while (transactionTimes.size() > 10_000) {
            transactionTimes.remove(transactionTimes.keySet().iterator().next());
        }
    }

    public synchronized int trackedAccountCount() {
        return transactionTimes.size();
    }
}