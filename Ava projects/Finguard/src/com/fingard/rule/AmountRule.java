package com.fingard.rule;

import com.fingard.model.Event;
import com.fingard.model.RiskResult;
import com.fingard.model.Transaction;

public class AmountRule extends FraudRule {
    private final double threshold;

    public AmountRule(double threshold) {
        super("FRAUD-AMOUNT", "Large Transaction");
        if (!Double.isFinite(threshold) || threshold <= 0) {
            throw new IllegalArgumentException("Amount threshold must be positive and finite.");
        }
        this.threshold = threshold;
    }

    public AmountRule() {
        this(10_000.00);
    }

    @Override
    public RiskResult evaluate(Event event) {
        if (!(event instanceof Transaction)) {
            return pass();
        }
        Transaction transaction = (Transaction) event;
        if (transaction.getAmount() > threshold) {
            return trigger(30, "Transaction amount exceeded configured threshold of " + threshold + ".");
        }
        return pass();
    }
}