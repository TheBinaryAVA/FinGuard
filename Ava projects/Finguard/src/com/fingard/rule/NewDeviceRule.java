package com.fingard.rule;

import com.fingard.model.DeviceEvent;
import com.fingard.model.Event;
import com.fingard.model.RiskResult;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NewDeviceRule extends ATORule {
    private final Map<String, Set<String>> knownDevices = new HashMap<>();
    private final Map<String, Instant> lastSeen = new HashMap<>();
    private final Duration retention = Duration.ofHours(24);

    public NewDeviceRule() {
        super("ATO-DEVICE", "New Device");
    }

    @Override
    public synchronized RiskResult evaluate(Event event) {
        if (!(event instanceof DeviceEvent)) {
            return pass();
        }
        DeviceEvent deviceEvent = (DeviceEvent) event;
        cleanupExpired(Instant.now());
        Set<String> devices = knownDevices.computeIfAbsent(deviceEvent.getAccountId(), key -> new HashSet<>());
        while (devices.size() >= 100) {
            devices.remove(devices.iterator().next());
        }
        boolean newDevice = devices.add(deviceEvent.getDeviceId());
        lastSeen.put(deviceEvent.getAccountId(), Instant.now());
        return newDevice ? trigger(20, "Device has not previously been associated with the account.") : pass();
    }

    private void cleanupExpired(Instant referenceTime) {
        Instant cutoff = referenceTime.minus(retention);
        knownDevices.entrySet().removeIf(entry -> {
            Instant seen = lastSeen.get(entry.getKey());
            if (seen != null && seen.isBefore(cutoff)) {
                lastSeen.remove(entry.getKey());
                return true;
            }
            return false;
        });
        while (knownDevices.size() > 10_000) {
            String accountId = knownDevices.keySet().iterator().next();
            knownDevices.remove(accountId);
            lastSeen.remove(accountId);
        }
    }
}