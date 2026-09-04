package com.fingard.model;

import com.fingard.exception.InvalidEventException;
import java.time.Instant;

public class DeviceEvent extends Event {
    private static final long serialVersionUID = 1L;
    private final String deviceId;
    private final String deviceModel;
    private final String osVersion;

    public DeviceEvent(String eventId, String accountId, Instant timestamp, String deviceId, String deviceModel, String osVersion) {
        super(eventId, accountId, timestamp, EventType.NEW_DEVICE);
        if (deviceId == null || deviceId.isBlank() || deviceModel == null || deviceModel.isBlank()
                || osVersion == null || osVersion.isBlank()) {
            throw new InvalidEventException("Device identity and software details are required.");
        }
        this.deviceId = deviceId;
        this.deviceModel = deviceModel;
        this.osVersion = osVersion;
    }

    public String getDeviceId() { return deviceId; }
    public String getDeviceModel() { return deviceModel; }
    public String getOsVersion() { return osVersion; }

    @Override
    public void displayEventInfo() {
        super.displayEventInfo();
        System.out.println("Device ID   : " + deviceId);
        System.out.println("Device Model: " + deviceModel);
        System.out.println("OS Version  : " + osVersion);
        System.out.println("===============================================");
    }
}