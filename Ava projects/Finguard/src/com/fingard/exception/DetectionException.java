package com.fingard.exception;

public class DetectionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public DetectionException(String message, Throwable cause) {
        super(message, cause);
    }
}