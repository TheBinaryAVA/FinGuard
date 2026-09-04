package com.fingard.util;

import java.util.concurrent.atomic.AtomicLong;

public final class IdGenerator {
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private IdGenerator() {
    }

    public static String next(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("ID prefix cannot be blank.");
        }
        return prefix + "-" + System.currentTimeMillis() + "-" + SEQUENCE.incrementAndGet();
    }
}