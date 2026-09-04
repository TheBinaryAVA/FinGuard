package com.fingard.exception;

public class InvalidEventException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}