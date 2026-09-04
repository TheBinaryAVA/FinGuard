package com.fingard.exception;

public class PersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}