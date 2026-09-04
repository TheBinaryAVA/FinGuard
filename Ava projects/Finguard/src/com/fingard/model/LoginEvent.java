package com.fingard.model;
import com.fingard.exception.InvalidEventException;
import java.time.Instant;
public class LoginEvent extends Event {
    private static final long serialVersionUID = 1L;
    private final String ipAddress;
    private final String location;
    private final boolean isSuccess;

    public LoginEvent(String eventId, String accountId, Instant timestamp, String ipAddress, String location, boolean isSuccess) {
        super(eventId, accountId, timestamp, EventType.LOGIN);
        if (ipAddress == null || ipAddress.isBlank() || location == null || location.isBlank()) {
            throw new InvalidEventException("Login IP address and location are required.");
        }
        this.ipAddress = ipAddress;
        this.location = location;
        this.isSuccess = isSuccess;
    }

    public String getIpAddress() { return ipAddress; }
    public String getLocation() { return location; }
    public boolean isSuccess() { return isSuccess; }

    @Override
    public void displayEventInfo() {
        super.displayEventInfo();
        System.out.println("IP Address : " + ipAddress);
        System.out.println("Location   : " + location);
        System.out.println("Status     : " + (isSuccess ? "SUCCESS" : "FAILED"));
        System.out.println("===============================================");
    }
}