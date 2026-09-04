package com.fingard.engine;

import com.fingard.model.BeneficiaryEvent;
import com.fingard.model.DeviceEvent;
import com.fingard.model.Event;
import com.fingard.model.EventType;
import com.fingard.model.LoginEvent;
import com.fingard.model.Transaction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class EventCorrelator {
    private final Duration window;
    private final int maxEventsPerAccount;
    private final Map<String, AccountState> history = new ConcurrentHashMap<>();
    private final AtomicLong recordsSinceCleanup = new AtomicLong();

    public EventCorrelator(Duration window, int maxEventsPerAccount) {
        if (window == null || window.isZero() || window.isNegative() || maxEventsPerAccount < 1) {
            throw new IllegalArgumentException("Correlation configuration is invalid.");
        }
        this.window = window;
        this.maxEventsPerAccount = maxEventsPerAccount;
    }

    public EventCorrelator() {
        this(Duration.ofHours(1), 200);
    }

    public CorrelationSignal record(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null.");
        }
        AccountState state = history.computeIfAbsent(event.getAccountId(), key -> new AccountState());
        CorrelationSignal signal;
        state.lock.lock();
        try {
            removeExpired(state, event.getTimestamp());
            if (state.eventIds.contains(event.getEventId())) {
                signal = summarize(state.events, event.getTimestamp(), true);
            } else {
                state.eventIds.add(event.getEventId());
                List<Event> ordered = new ArrayList<>(state.events);
                ordered.add(event);
                ordered.sort(Comparator.comparing(Event::getTimestamp));
                state.events.clear();
                state.events.addAll(ordered);
                while (state.events.size() > maxEventsPerAccount) {
                    Event removed = state.events.removeFirst();
                    state.eventIds.remove(removed.getEventId());
                }
                signal = summarize(state.events, event.getTimestamp(), false);
            }
        } finally {
            state.lock.unlock();
        }
        if (recordsSinceCleanup.incrementAndGet() >= 256) {
            cleanupExpiredAccounts(Instant.now());
        }
        return signal;
    }

    public CorrelationSignal snapshot(String accountId, Instant referenceTime) {
        if (accountId == null || accountId.isBlank() || referenceTime == null) {
            throw new IllegalArgumentException("Account and reference time are required.");
        }
        AccountState state = history.get(accountId);
        if (state == null) {
            return CorrelationSignal.empty();
        }
        state.lock.lock();
        try {
            removeExpired(state, referenceTime);
            return summarize(state.events, referenceTime, false);
        } finally {
            state.lock.unlock();
        }
    }

    public int accountHistorySize(String accountId) {
        AccountState state = history.get(accountId);
        if (state == null) {
            return 0;
        }
        state.lock.lock();
        try {
            removeExpired(state, Instant.now());
            return state.events.size();
        } finally {
            state.lock.unlock();
        }
    }

    private void removeExpired(AccountState state, Instant referenceTime) {
        Instant cutoff = referenceTime.minus(window);
        state.events.removeIf(existing -> {
            boolean expired = existing.getTimestamp().isBefore(cutoff);
            if (expired) {
                state.eventIds.remove(existing.getEventId());
            }
            return expired;
        });
    }

    private void cleanupExpiredAccounts(Instant referenceTime) {
        recordsSinceCleanup.set(0);
        for (Map.Entry<String, AccountState> entry : history.entrySet()) {
            AccountState state = entry.getValue();
            state.lock.lock();
            try {
                removeExpired(state, referenceTime);
                if (state.events.isEmpty()) {
                    history.remove(entry.getKey(), state);
                }
            } finally {
                state.lock.unlock();
            }
        }
    }

    private CorrelationSignal summarize(Deque<Event> accountHistory, Instant referenceTime, boolean duplicate) {
        int failedLogins = 0;
        int transactions = 0;
        boolean recentDevice = false;
        boolean recentBeneficiary = false;
        boolean suspiciousSequence = false;
        List<Event> ordered = new ArrayList<>(accountHistory);
        for (Event event : ordered) {
            if (event.getTimestamp().isAfter(referenceTime)) {
                continue;
            }
            if (event instanceof LoginEvent && !((LoginEvent) event).isSuccess()) {
                failedLogins++;
            } else if (event instanceof Transaction) {
                transactions++;
            } else if (event instanceof DeviceEvent) {
                recentDevice = true;
            } else if (event instanceof BeneficiaryEvent) {
                recentBeneficiary = true;
            }
        }
        if (recentDevice && recentBeneficiary && transactions > 0) {
            suspiciousSequence = sequenceExists(ordered);
        }
        return new CorrelationSignal(failedLogins, transactions, recentDevice, recentBeneficiary, suspiciousSequence, duplicate);
    }

    private boolean sequenceExists(List<Event> events) {
        boolean sawDevice = false;
        boolean sawBeneficiary = false;
        for (Event event : events) {
            if (event.getEventType() == EventType.NEW_DEVICE) {
                sawDevice = true;
            } else if (sawDevice && event.getEventType() == EventType.BENEFICIARY_ADDED) {
                sawBeneficiary = true;
            } else if (sawBeneficiary && event.getEventType() == EventType.TRANSACTION) {
                return true;
            }
        }
        return false;
    }

    public static final class CorrelationSignal {
        private final int failedLoginCount;
        private final int transactionCount;
        private final boolean recentDevice;
        private final boolean recentBeneficiary;
        private final boolean suspiciousSequence;
        private final boolean duplicate;

        private CorrelationSignal(int failedLoginCount, int transactionCount, boolean recentDevice,
                      boolean recentBeneficiary, boolean suspiciousSequence, boolean duplicate) {
            this.failedLoginCount = failedLoginCount;
            this.transactionCount = transactionCount;
            this.recentDevice = recentDevice;
            this.recentBeneficiary = recentBeneficiary;
            this.suspiciousSequence = suspiciousSequence;
            this.duplicate = duplicate;
        }

        public static CorrelationSignal empty() {
            return new CorrelationSignal(0, 0, false, false, false, false);
        }

        public int getFailedLoginCount() { return failedLoginCount; }
        public int getTransactionCount() { return transactionCount; }
        public boolean hasRecentDevice() { return recentDevice; }
        public boolean hasRecentBeneficiary() { return recentBeneficiary; }
        public boolean hasSuspiciousSequence() { return suspiciousSequence; }
        public boolean isDuplicate() { return duplicate; }
    }

    private static final class AccountState {
        private final Deque<Event> events = new ArrayDeque<>();
        private final Set<String> eventIds = new HashSet<>();
        private final ReentrantLock lock = new ReentrantLock();
    }
}