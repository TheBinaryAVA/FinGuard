package com.fingard.database;

import com.fingard.config.DatabaseConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private final String url;
    private final String user;
    private final String password;

    public DatabaseConnection(String host, int port, String database, String user, String password) {
        this(new DatabaseConfig(host, port, database, user, password));
    }

    public DatabaseConnection(DatabaseConfig config) {
        this.url = "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase()
                + "?useSSL=false&serverTimezone=UTC";
        this.user = config.getUser();
        this.password = config.getPassword();
    }

    public static DatabaseConnection fromEnvironment() {
        return new DatabaseConnection(DatabaseConfig.load());
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}