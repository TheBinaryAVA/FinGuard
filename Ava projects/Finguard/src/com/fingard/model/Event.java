package com.fingard.model;

import com.fingard.exception.InvalidEventException;
import java.io.Serializable;
import java.time.Instant;

public abstract class Event implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String eventId;
    private final String accountId;
    private final Instant timestamp;
    private final EventType eventType;

    public Event(String eventId, String accountId, Instant timestamp, EventType eventType) {
        if (eventId == null || eventId.isBlank()) {
            throw new InvalidEventException("Event ID cannot be null or blank.");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new InvalidEventException("Account ID cannot be null or blank.");
        }
        this.eventId = eventId;
        this.accountId = accountId;
        this.timestamp = (timestamp != null) ? timestamp : Instant.now();
        if (eventType == null) {
            throw new InvalidEventException("Event type cannot be null.");
        }
        this.eventType = eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void displayEventInfo() {
        System.out.println("================ EVENT DETAILS ================");
        System.out.println("Event ID   : " + eventId);
        System.out.println("Account ID : " + accountId);
        System.out.println("Timestamp  : " + timestamp);
        System.out.println("Event Type : " + eventType);
    }
}