package com.fingard.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DatabaseConfig {
    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    public DatabaseConfig(String host, int port, String database, String user, String password) {
        if (host == null || host.isBlank() || port < 1 || port > 65535 || database == null || database.isBlank()
                || user == null || user.isBlank() || password == null) {
            throw new IllegalArgumentException("Database configuration is incomplete or invalid.");
        }
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    public static DatabaseConfig load() {
        return load(Path.of("config", "db.properties"));
    }

    public static DatabaseConfig load(Path propertiesPath) {
        Properties properties = new Properties();
        if (propertiesPath != null && Files.exists(propertiesPath)) {
            try (InputStream input = Files.newInputStream(propertiesPath)) {
                properties.load(input);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Unable to read database properties.", exception);
            }
        }
        return new DatabaseConfig(
                setting("FINGUARD_DB_HOST", "db.host", properties, "localhost"),
                integerSetting("FINGUARD_DB_PORT", "db.port", properties, 3306),
                setting("FINGUARD_DB_NAME", "db.name", properties, "finguard"),
                setting("FINGUARD_DB_USER", "db.user", properties, "root"),
                setting("FINGUARD_DB_PASSWORD", "db.password", properties, ""));
    }

    private static String setting(String environmentName, String propertyName, Properties properties, String fallback) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        return properties.getProperty(propertyName, fallback);
    }

    private static int integerSetting(String environmentName, String propertyName, Properties properties, int fallback) {
        String value = setting(environmentName, propertyName, properties, Integer.toString(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Database port must be numeric.", exception);
        }
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUser() { return user; }
    public String getPassword() { return password; }
}