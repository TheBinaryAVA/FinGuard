package com.fingard.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

public class Logger {
    private final Path logFile;
    private final ReentrantLock lock = new ReentrantLock();

    public Logger(Path logFile) {
        this.logFile = logFile;
        try {
            Path parent = logFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to initialize log directory.", exception);
        }
    }

    public void info(String component, String message) {
        write("INFO", component, message, null);
    }

    public void warn(String component, String message) {
        write("WARN", component, message, null);
    }

    public void error(String component, String message, Throwable failure) {
        write("ERROR", component, message, failure);
    }

    private void write(String level, String component, String message, Throwable failure) {
        String line = Instant.now() + " [" + level + "] [" + component + "] " + message
                + (failure == null ? "" : " cause=" + failure.getClass().getSimpleName() + ": " + failure.getMessage())
                + System.lineSeparator();
        lock.lock();
        try {
            System.out.print(line);
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            System.err.println(Instant.now() + " [ERROR] [Logger] Unable to write log: " + exception.getMessage());
        } finally {
            lock.unlock();
        }
    }
}