package com.fingard.persistence;

import com.fingard.database.DatabaseConnection;
import com.fingard.exception.PersistenceException;
import com.fingard.model.Account;
import com.fingard.model.Alert;
import com.fingard.model.Event;
import com.fingard.model.RiskResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcRepository<T extends Serializable> implements Repository<T> {
    private final Class<T> entityType;
    private final DatabaseConnection database;

    public JdbcRepository(Class<T> entityType, DatabaseConnection database) {
        this.entityType = entityType;
        this.database = database;
    }

    @Override
    public void save(String id, T entity) {
        if (id == null || id.isBlank() || entity == null) {
            throw new IllegalArgumentException("Repository ID and entity are required.");
        }
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                if (entity instanceof Account) {
                    saveAccount(connection, id, (Account) entity);
                } else if (entity instanceof Event) {
                    saveEvent(connection, id, (Event) entity);
                } else if (entity instanceof RiskResult) {
                    saveRiskResult(connection, id, (RiskResult) entity);
                } else if (entity instanceof Alert) {
                    saveAlert(connection, id, (Alert) entity);
                } else {
                    throw new IllegalArgumentException("Unsupported JDBC entity type: " + entityType.getName());
                }
                connection.commit();
            } catch (SQLException | IOException | RuntimeException exception) {
                rollback(connection, exception);
                throw new PersistenceException("JDBC transaction rolled back for record " + id, exception);
            }
        } catch (PersistenceException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PersistenceException("Unable to save JDBC record " + id, exception);
        }
    }

    private void saveAccount(Connection connection, String id, Account account) throws SQLException, IOException {
        String sql = "INSERT INTO accounts(account_id, customer_name, account_status, payload) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE customer_name=VALUES(customer_name), account_status=VALUES(account_status), payload=VALUES(payload)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, account.getCustomerName());
            statement.setString(3, account.getAccountStatus().name());
            statement.setBytes(4, serialize(account));
            statement.executeUpdate();
        }
    }

    private void saveEvent(Connection connection, String id, Event event) throws SQLException, IOException {
        String sql = "INSERT INTO events(event_id, account_id, event_type, event_timestamp, payload) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE payload=VALUES(payload)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, event.getAccountId());
            statement.setString(3, event.getEventType().name());
            statement.setTimestamp(4, java.sql.Timestamp.from(event.getTimestamp()));
            statement.setBytes(5, serialize(event));
            statement.executeUpdate();
        }
    }

    private void saveRiskResult(Connection connection, String id, RiskResult result) throws SQLException, IOException {
        String sql = "INSERT INTO risk_results(event_id, score, risk_level, decision, payload) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE score=VALUES(score), risk_level=VALUES(risk_level), decision=VALUES(decision), payload=VALUES(payload)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setInt(2, result.getScore());
            statement.setString(3, result.getRiskLevel().name());
            statement.setString(4, result.getDecision().name());
            statement.setBytes(5, serialize(result));
            statement.executeUpdate();
        }
    }

    private void saveAlert(Connection connection, String id, Alert alert) throws SQLException, IOException {
        String sql = "INSERT INTO alerts(alert_id, account_id, event_id, alert_timestamp, score, decision, payload) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, alert.getAccountId());
            statement.setString(3, alert.getEventId());
            statement.setTimestamp(4, java.sql.Timestamp.from(alert.getTimestamp()));
            statement.setInt(5, alert.getRiskResult().getScore());
            statement.setString(6, alert.getRiskResult().getDecision().name());
            statement.setBytes(7, serialize(alert));
            statement.executeUpdate();
        }
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    @Override
    public Optional<T> findById(String id) {
        String table = tableFor(entityType);
        String idColumn = entityType == RiskResult.class ? "event_id" : entityType == Event.class ? "event_id" : entityType == Alert.class ? "alert_id" : "account_id";
        String sql = "SELECT payload FROM " + table + " WHERE " + idColumn + " = ?";
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(deserialize(resultSet.getBytes(1))) : Optional.empty();
            }
        } catch (SQLException | IOException | ClassNotFoundException exception) {
            throw new PersistenceException("Unable to load JDBC record " + id, exception);
        }
    }

    @Override
    public List<T> findAll() {
        String table = tableFor(entityType);
        String sql = "SELECT payload FROM " + table;
        List<T> results = new ArrayList<>();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(deserialize(resultSet.getBytes(1)));
            }
            return results;
        } catch (SQLException | IOException | ClassNotFoundException exception) {
            throw new PersistenceException("Unable to load JDBC records from " + table, exception);
        }
    }

    private String tableFor(Class<T> type) {
        if (Account.class.isAssignableFrom(type)) return "accounts";
        if (Event.class.isAssignableFrom(type)) return "events";
        if (RiskResult.class.isAssignableFrom(type)) return "risk_results";
        if (Alert.class.isAssignableFrom(type)) return "alerts";
        throw new IllegalArgumentException("Unsupported JDBC entity type: " + type.getName());
    }

    private byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) input.readObject();
        }
    }
}