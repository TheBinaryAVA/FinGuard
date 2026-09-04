package com.fingard.api;

import com.fingard.model.Alert;
import com.fingard.model.BeneficiaryEvent;
import com.fingard.model.DeviceEvent;
import com.fingard.model.Event;
import com.fingard.model.EventType;
import com.fingard.model.LoginEvent;
import com.fingard.model.RiskResult;
import com.fingard.model.Transaction;
import com.fingard.persistence.Repository;
import com.fingard.queue.EventQueue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiServer implements AutoCloseable {
    private final HttpServer server;
    private final EventQueue queue;
    private final Repository<RiskResult> riskRepository;
    private final Repository<Alert> alertRepository;
    private final ExecutorService executor;

    public ApiServer(String host, int port, EventQueue queue,
                     Repository<RiskResult> riskRepository, Repository<Alert> alertRepository) throws IOException {
        if (host == null || host.isBlank() || port < 1 || port > 65535
                || queue == null || riskRepository == null || alertRepository == null) {
            throw new IllegalArgumentException("API server configuration is invalid.");
        }
        this.queue = queue;
        this.riskRepository = riskRepository;
        this.alertRepository = alertRepository;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 32);
        this.server.createContext("/", new DashboardHandler());
        this.server.createContext("/api/health", new HealthHandler());
        this.server.createContext("/api/ingest", new IngestHandler());
        this.server.createContext("/api/results", new ResultsHandler());
        this.server.createContext("/api/alerts", new AlertsHandler());
        this.executor = Executors.newFixedThreadPool(4);
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(Math.max(0, delaySeconds));
        executor.shutdownNow();
    }

    @Override
    public void close() {
        stop(1);
    }

    private abstract static class JsonHandler implements HttpHandler {
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    send(exchange, 405, "{\"error\":\"method_not_allowed\"}", "application/json");
                    return;
                }
                handleGet(exchange);
            } catch (RuntimeException exception) {
                send(exchange, 500, "{\"error\":\"internal_error\"}", "application/json");
            }
        }

        protected abstract void handleGet(HttpExchange exchange) throws IOException;
    }

    private final class HealthHandler extends JsonHandler {
        @Override
        protected void handleGet(HttpExchange exchange) throws IOException {
            send(exchange, 200, "{\"status\":\"UP\",\"queueSize\":" + queue.size()
                    + ",\"queueShutdown\":" + queue.isShutdown() + "}", "application/json");
        }
    }

    private final class ResultsHandler extends JsonHandler {
        @Override
        protected void handleGet(HttpExchange exchange) throws IOException {
            List<RiskResult> results = riskRepository.findAll();
            StringBuilder body = new StringBuilder("[ ");
            for (int index = 0; index < results.size(); index++) {
                if (index > 0) body.append(',');
                body.append(resultJson(results.get(index)));
            }
            body.append(" ]");
            send(exchange, 200, body.toString(), "application/json");
        }
    }

    private final class AlertsHandler extends JsonHandler {
        @Override
        protected void handleGet(HttpExchange exchange) throws IOException {
            List<Alert> alerts = alertRepository.findAll();
            StringBuilder body = new StringBuilder("[ ");
            for (int index = 0; index < alerts.size(); index++) {
                if (index > 0) body.append(',');
                Alert alert = alerts.get(index);
                body.append("{\"alertId\":").append(json(alert.getAlertId()))
                        .append(",\"accountId\":").append(json(alert.getAccountId()))
                        .append(",\"eventId\":").append(json(alert.getEventId()))
                        .append(",\"timestamp\":").append(json(alert.getTimestamp().toString()))
                        .append(",\"result\":").append(resultJson(alert.getRiskResult())).append('}');
            }
            body.append(" ]");
            send(exchange, 200, body.toString(), "application/json");
        }
    }

    private final class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
                return;
            }
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                send(exchange, 404, "Not Found", "text/plain; charset=utf-8");
                return;
            }
            Path dashboard = Path.of("webroot", "index.html").toAbsolutePath().normalize();
            if (!Files.isRegularFile(dashboard)) {
                send(exchange, 404, "Dashboard not found", "text/plain; charset=utf-8");
                return;
            }
            send(exchange, 200, Files.readString(dashboard, StandardCharsets.UTF_8), "text/html; charset=utf-8");
        }
    }

    private final class IngestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "{\"error\":\"method_not_allowed\"}", "application/json");
                return;
            }
            try {
                byte[] bytes;
                try (java.io.InputStream input = exchange.getRequestBody()) {
                    bytes = input.readAllBytes();
                }
                if (bytes.length > 1_048_576) {
                    throw new IllegalArgumentException("Request body is too large.");
                }
                Event event = parseEvent(new String(bytes, StandardCharsets.UTF_8));
                queue.put(event);
                send(exchange, 202, "{\"accepted\":true,\"eventId\":" + json(event.getEventId()) + "}", "application/json");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                send(exchange, 503, "{\"error\":\"service_unavailable\"}", "application/json");
            } catch (IllegalArgumentException exception) {
                send(exchange, 400, "{\"error\":" + json(exception.getMessage()) + "}", "application/json");
            } catch (IllegalStateException exception) {
                send(exchange, 503, "{\"error\":\"service_unavailable\"}", "application/json");
            }
        }
    }

    private static Event parseEvent(String body) {
        String type = requiredString(body, "type").toUpperCase(java.util.Locale.ROOT);
        String eventId = requiredString(body, "eventId");
        String accountId = requiredString(body, "accountId");
        String timestampValue = optionalString(body, "timestamp");
        Instant timestamp = timestampValue == null ? Instant.now() : parseTimestamp(timestampValue);
        try {
            switch (EventType.valueOf(type)) {
                case LOGIN:
                    return new LoginEvent(eventId, accountId, timestamp, requiredString(body, "ipAddress"),
                            requiredString(body, "location"), requiredBoolean(body, "success"));
                case TRANSACTION:
                    return new Transaction(eventId, accountId, timestamp, requiredDouble(body, "amount"),
                            requiredString(body, "currency"), requiredString(body, "recipientAccountId"));
                case NEW_DEVICE:
                    return new DeviceEvent(eventId, accountId, timestamp, requiredString(body, "deviceId"),
                            requiredString(body, "deviceModel"), requiredString(body, "osVersion"));
                case BENEFICIARY_ADDED:
                    return new BeneficiaryEvent(eventId, accountId, timestamp,
                            requiredString(body, "beneficiaryAccountId"), requiredString(body, "beneficiaryName"));
                default:
                    throw new IllegalArgumentException("Unsupported event type.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid event: " + exception.getMessage(), exception);
        }
    }

    private static final Pattern FIELD = Pattern.compile("\\\"([A-Za-z][A-Za-z0-9]*)\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|true|false|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)");

    private static String rawField(String body, String name) {
        Matcher matcher = FIELD.matcher(body);
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) return matcher.group(2);
        }
        return null;
    }

    private static String requiredString(String body, String name) {
        String raw = optionalString(body, name);
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Missing field: " + name);
        return raw;
    }

    private static String optionalString(String body, String name) {
        String raw = rawField(body, name);
        if (raw == null || !raw.startsWith("\"")) return null;
        return raw.substring(1, raw.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static boolean requiredBoolean(String body, String name) {
        String raw = rawField(body, name);
        if (!"true".equals(raw) && !"false".equals(raw)) throw new IllegalArgumentException("Invalid boolean field: " + name);
        return Boolean.parseBoolean(raw);
    }

    private static double requiredDouble(String body, String name) {
        String raw = rawField(body, name);
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid numeric field: " + name, exception);
        }
    }

    private static Instant parseTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid timestamp.", exception);
        }
    }

    private static String resultJson(RiskResult result) {
        return "{\"eventId\":" + json(result.getEventId()) + ",\"score\":" + result.getScore()
                + ",\"level\":" + json(result.getRiskLevel().name()) + ",\"decision\":"
                + json(result.getDecision().name()) + ",\"rules\":" + stringArray(result.getTriggeredRules())
                + ",\"reasons\":" + stringArray(result.getReasons()) + '}';
    }

    private static String stringArray(List<String> values) {
        StringBuilder body = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) body.append(',');
            body.append(json(values.get(index)));
        }
        return body.append(']').toString();
    }

    private static String json(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final String DASHBOARD = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>FinGuard Risk Console</title><style>
            :root{color-scheme:dark;font-family:ui-sans-serif,system-ui,sans-serif;background:#101418;color:#edf2f4}*{box-sizing:border-box}
            body{margin:0;background:radial-gradient(circle at 15% 0,#263b43,transparent 36%),#101418;min-height:100vh}
            main{max-width:1180px;margin:auto;padding:42px 24px}.eyebrow{color:#80cbc4;letter-spacing:.15em;text-transform:uppercase;font-size:12px;font-weight:700}
            h1{font-size:clamp(34px,6vw,68px);line-height:.95;margin:12px 0 10px;max-width:700px}p{color:#a8b4b9}
            .bar{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #304148;padding:18px 0;margin-bottom:22px}
            .status{color:#80cbc4}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:24px 0}.stat,section{border:1px solid #304148;background:#172126;padding:18px;border-radius:6px}.stat strong{display:block;font-size:30px;margin-top:8px}
            section{margin-top:14px}h2{font-size:16px;margin:0 0 14px}.row{display:grid;grid-template-columns:1fr 110px 100px 100px;gap:12px;padding:12px 0;border-top:1px solid #2b3a40;font-size:13px}.tag{font-weight:700}.BLOCK{color:#ff8a80}.REVIEW{color:#ffd180}.ALLOW{color:#80cbc4}
            @media(max-width:720px){main{padding:28px 14px}.grid{grid-template-columns:1fr}.row{grid-template-columns:1fr 80px 80px}.row span:last-child{grid-column:1/-1}}
            </style></head><body><main><div class="eyebrow">FinGuard / live risk console</div><h1>Decisions with context.</h1><p>Deterministic fraud and account takeover signals, correlated in bounded time windows.</p>
            <div class="bar"><span>System status</span><strong id="status" class="status">Connecting...</strong></div><div class="grid"><div class="stat">Queue depth<strong id="queue">-</strong></div><div class="stat">Risk results<strong id="resultCount">-</strong></div><div class="stat">Alerts<strong id="alertCount">-</strong></div></div>
            <section><h2>Recent risk results</h2><div id="results">Loading...</div></section><section><h2>Alerts</h2><div id="alerts">Loading...</div></section></main><script>
            const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
            async function refresh(){try{const [h,r,a]=await Promise.all(['/api/health','/api/results','/api/alerts'].map(x=>fetch(x).then(y=>y.json())));status.textContent=h.status;queue.textContent=h.queueSize;resultCount.textContent=r.length;alertCount.textContent=a.length;results.innerHTML=r.slice(-12).reverse().map(x=>`<div class="row"><span>${esc(x.eventId)}</span><span>${x.score}</span><span>${esc(x.level)}</span><span class="tag ${x.decision}">${esc(x.decision)}</span></div>`).join('')||'No results';alerts.innerHTML=a.slice(-8).reverse().map(x=>`<div class="row"><span>${esc(x.eventId)}</span><span>${x.result.score}</span><span>${esc(x.result.level)}</span><span class="tag ${x.result.decision}">${esc(x.result.decision)}</span></div>`).join('')||'No alerts'}catch(e){status.textContent='Unavailable'}}refresh();setInterval(refresh,3000);
            </script></body></html>
            """;
}