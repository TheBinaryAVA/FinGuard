# FinGuard

Financial Fraud and Account Takeover Detection Platform built with plain Java.

## Current implementation

FinGuard processes authentication, device, beneficiary, and transaction events using deterministic rules. It keeps bounded per-account history, correlates suspicious sequences, calculates a score from 0 to 100, and emits `ALLOW`, `REVIEW`, or `BLOCK` decisions.

Implemented components include:

- Serializable domain events and risk results
- Polymorphic fraud and account-takeover rules
- Large-amount, velocity, new-device, and login-failure detection
- One-hour bounded event correlation with account isolation
- Risk scoring, risk levels, explanations, and alerts
- Bounded producer/consumer queue using `ReentrantLock` and `Condition`
- Graceful worker shutdown with interruption handling
- File snapshots using object streams and text audit records
- Generic repository abstraction
- Raw JDBC repository with prepared statements and commit/rollback handling
- Configurable MySQL connection settings from environment variables or `config/db.properties`
- Thread-safe console/file logging
- Security and reliability audit checks for malformed input, stress, outages, and shutdown races
- JDK `HttpServer` dashboard with health, result, and alert endpoints

## Architecture

```text
Events -> EventQueue -> EventWorker(s) -> DetectionEngine
                                      -> EventCorrelator
                                      -> RiskEngine
                                      -> RiskResult / Alert
                                      -> FileRepository or JdbcRepository
                                      -> ApiServer dashboard
```

The event hierarchy is `Event` with `Transaction`, `LoginEvent`, `DeviceEvent`, and `BeneficiaryEvent` subclasses. Rules implement `DetectionRule` and are grouped under `FraudRule` or `ATORule`.

## Detection and scoring

The default rule contributions are:

- Large transaction: 30
- Transaction velocity: 25
- New device: 20
- Login failure burst: 20
- Historical failed-login context: 10
- Recent device plus beneficiary context: 20
- Ordered account-takeover sequence: 20

Scores are capped at 100. The demo uses a review threshold of 30 and a block threshold of 75. ATO correlation considers the account, event timestamps, event types, and sequence order inside a bounded one-hour window.

## Concurrency

`EventQueue` is bounded and uses `ReentrantLock` with `notEmpty` and `notFull` conditions. Producers block when the queue is full. Shutdown wakes all waiters, allows queued events to drain, and causes workers to exit cleanly once the queue is empty.

## Persistence

`FileRepository` stores a serialized map in `data/` and appends a small text audit record. `JdbcRepository` stores serialized payloads alongside indexed query fields. JDBC credentials are read from:

```text
FINGUARD_DB_HOST
FINGUARD_DB_PORT
FINGUARD_DB_NAME
FINGUARD_DB_USER
FINGUARD_DB_PASSWORD
```

Configuration is resolved from environment variables first, then `config/db.properties`, then local-development defaults. Copy [config/db.properties.example](config/db.properties.example) to `config/db.properties` for local use. Production passwords should be supplied through the environment or an external secret manager; the real properties file is ignored by Git.

The schema is in [database/schema.sql](database/schema.sql). A MySQL JDBC driver must be supplied by the application environment; no third-party dependency is bundled in this source-only project.

## Run

Requires Java 17 or newer. From the project directory:

```powershell
$out = Join-Path $env:TEMP "finguard-classes"
New-Item -ItemType Directory -Force $out | Out-Null
javac -d $out (Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
java -cp $out com.fingard.Main
```

`Main` runs a legitimate transaction, a large transaction, an ordered ATO sequence, and a concurrent multi-worker workload. File snapshots and logs are generated at runtime and are ignored by Git.

## HTTP dashboard

`ApiServer` uses the JDK HTTP server and has no web framework dependency. The bootstrap serves a dashboard at `http://127.0.0.1:8080/` and JSON endpoints:

- `GET /api/health` reports status, queue depth, and shutdown state.
- `GET /api/results` returns persisted risk results with scores, decisions, rules, and reasons.
- `GET /api/alerts` returns persisted alerts and their risk results.

The page refreshes its read-only view every three seconds. The server owns a bounded executor and shuts it down during application close.

## Audit checks

The executable harness also verifies invalid-event rejection, duplicate and out-of-order event handling, bounded velocity windows, 100 events submitted by four producers, worker termination, simulated JDBC outage handling, and a producer-versus-shutdown race.

The correlator uses per-account locks, timestamp-ordered bounded histories, duplicate event IDs, and stale-account cleanup. Workers isolate failures to individual events so one persistence or detection failure does not terminate the worker pool.

## Limitations

This is a single-node deterministic rule engine. It has no distributed broker, HTTP API, authentication layer, external alert service, or horizontal state replication. JDBC support is implemented as an adapter, but requires a MySQL driver and database supplied by the runtime environment.