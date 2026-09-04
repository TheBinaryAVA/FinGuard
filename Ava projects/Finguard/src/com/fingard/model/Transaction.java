package com.fingard.model;

import com.fingard.exception.InvalidEventException;
import java.time.Instant;

public class Transaction extends Event {
    private static final long serialVersionUID = 1L;
    private final double amount;
    private final String currency;
    private final String recipientAccountId;

    public Transaction(String eventId, String accountId, Instant timestamp, double amount, String currency, String recipientAccountId) {
        super(eventId, accountId, timestamp, EventType.TRANSACTION);
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new InvalidEventException("Transaction amount must be positive and finite.");
        }
        if (currency == null || currency.isBlank() || recipientAccountId == null || recipientAccountId.isBlank()) {
            throw new InvalidEventException("Currency and recipient account are required.");
        }
        this.amount = amount;
        this.currency = currency;
        this.recipientAccountId = recipientAccountId;
    }

    public double getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRecipientAccountId() { return recipientAccountId; }

    @Override
    public void displayEventInfo() {
        super.displayEventInfo();
        System.out.println("Amount    : " + amount + " " + currency);
        System.out.println("Recipient : " + recipientAccountId);
        System.out.println("===============================================");
    }
}