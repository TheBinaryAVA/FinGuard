package com.fingard.queue;

import com.fingard.model.Event;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class EventQueue {
    private final int capacity;
    private final Deque<Event> events = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private boolean shutdown;

    public EventQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Queue capacity must be positive.");
        }
        this.capacity = capacity;
    }

    public void put(Event event) throws InterruptedException {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null.");
        }
        lock.lockInterruptibly();
        try {
            while (events.size() == capacity && !shutdown) {
                notFull.await();
            }
            if (shutdown) {
                throw new IllegalStateException("Queue is shut down.");
            }
            events.addLast(event);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public Event take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (events.isEmpty() && !shutdown) {
                notEmpty.await();
            }
            if (events.isEmpty()) {
                return null;
            }
            Event event = events.removeFirst();
            notFull.signal();
            return event;
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            shutdown = true;
            notEmpty.signalAll();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return events.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isShutdown() {
        lock.lock();
        try {
            return shutdown;
        } finally {
            lock.unlock();
        }
    }
}