CREATE TABLE IF NOT EXISTS accounts (
    account_id VARCHAR(64) PRIMARY KEY,
    customer_name VARCHAR(200) NOT NULL,
    account_status VARCHAR(32) NOT NULL,
    payload BLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS events (
    event_id VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_timestamp TIMESTAMP(6) NOT NULL,
    payload BLOB NOT NULL,
    CONSTRAINT fk_events_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    INDEX idx_events_account_time (account_id, event_timestamp),
    INDEX idx_events_type_time (event_type, event_timestamp)
);

CREATE TABLE IF NOT EXISTS risk_results (
    event_id VARCHAR(64) PRIMARY KEY,
    score INT NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    payload BLOB NOT NULL,
    CONSTRAINT fk_results_event FOREIGN KEY (event_id) REFERENCES events(event_id)
);

CREATE TABLE IF NOT EXISTS alerts (
    alert_id VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    alert_timestamp TIMESTAMP(6) NOT NULL,
    score INT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    payload BLOB NOT NULL,
    CONSTRAINT fk_alerts_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_alerts_event FOREIGN KEY (event_id) REFERENCES events(event_id),
    INDEX idx_alerts_account_time (account_id, alert_timestamp)
);