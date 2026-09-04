package com.fingard.model;

import java.io.Serializable;

public class Account implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String accountId; // Immutable identifier
    private String customerName;
    private AccountStatus accountStatus;

    public enum AccountStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        SUSPENDED,
        CLOSED
    }

    public Account(String accountId, String customerName, AccountStatus accountStatus) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be null or blank.");
        }
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be null or blank.");
        }
        if (accountStatus == null) {
            throw new IllegalArgumentException("Account status cannot be null.");
        }
        this.accountId = accountId;
        this.customerName = customerName;
        this.accountStatus = accountStatus;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be null or blank.");
        }
        this.customerName = customerName;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        if (accountStatus == null) {
            throw new IllegalArgumentException("Account status cannot be null.");
        }
        this.accountStatus = accountStatus;
    }

    public void displayAccountInfo() {
        System.out.println("---------------- ACCOUNT INFO ----------------");
        System.out.println("Account ID     : " + accountId);
        System.out.println("Customer Name  : " + customerName);
        System.out.println("Account Status : " + accountStatus);
        System.out.println("----------------------------------------------");
    }
}