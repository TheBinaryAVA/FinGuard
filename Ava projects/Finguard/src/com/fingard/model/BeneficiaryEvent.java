package com.fingard.model;

import com.fingard.exception.InvalidEventException;
import java.time.Instant;

public class BeneficiaryEvent extends Event {
    private static final long serialVersionUID = 1L;
    private final String beneficiaryAccountId;
    private final String beneficiaryName;

    public BeneficiaryEvent(String eventId, String accountId, Instant timestamp, String beneficiaryAccountId, String beneficiaryName) {
        super(eventId, accountId, timestamp, EventType.BENEFICIARY_ADDED);
        if (beneficiaryAccountId == null || beneficiaryAccountId.isBlank()
                || beneficiaryName == null || beneficiaryName.isBlank()) {
            throw new InvalidEventException("Beneficiary account and name are required.");
        }
        this.beneficiaryAccountId = beneficiaryAccountId;
        this.beneficiaryName = beneficiaryName;
    }

    public String getBeneficiaryAccountId() { return beneficiaryAccountId; }
    public String getBeneficiaryName() { return beneficiaryName; }

    @Override
    public void displayEventInfo() {
        super.displayEventInfo();
        System.out.println("Beneficiary Acc : " + beneficiaryAccountId);
        System.out.println("Beneficiary Name: " + beneficiaryName);
        System.out.println("===============================================");
    }
}