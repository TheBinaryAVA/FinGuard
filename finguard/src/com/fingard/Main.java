package com.fingard;

import com.fingard.api.ApiServer;
import com.fingard.engine.DetectionEngine;
import com.fingard.engine.EventCorrelator;
import com.fingard.engine.RiskEngine;
import com.fingard.model.Alert;
import com.fingard.model.Event;
import com.fingard.model.RiskResult;
import com.fingard.persistence.FileRepository;
import com.fingard.persistence.Repository;
import com.fingard.queue.EventQueue;
import com.fingard.rule.AmountRule;
import com.fingard.rule.DetectionRule;
import com.fingard.rule.LoginFailureRule;
import com.fingard.rule.NewDeviceRule;
import com.fingard.rule.VelocityRule;
import com.fingard.util.Logger;
import com.fingard.worker.EventWorker;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        EventQueue queue = new EventQueue(1_000);
        EventCorrelator correlator = new EventCorrelator();
        RiskEngine riskEngine = new RiskEngine();
        List<DetectionRule> rules = Arrays.asList(
                new AmountRule(), new VelocityRule(), new NewDeviceRule(), new LoginFailureRule());
        DetectionEngine detectionEngine = new DetectionEngine(rules, correlator, riskEngine);

        Path dataDirectory = Path.of("data", "snapshots");
        Repository<Event> eventRepository = new FileRepository<>(dataDirectory, "events");
        Repository<RiskResult> riskRepository = new FileRepository<>(dataDirectory, "risk-results");
        Repository<Alert> alertRepository = new FileRepository<>(dataDirectory, "alerts");
        Logger logger = new Logger(Path.of("logs", "finguard.log"));

        Thread[] workers = new Thread[4];
        for (int index = 0; index < workers.length; index++) {
            workers[index] = new Thread(new EventWorker("worker-" + (index + 1), queue,
                    detectionEngine, eventRepository, riskRepository, alertRepository, logger));
            workers[index].start();
        }

        ApiServer apiServer = new ApiServer("127.0.0.1", 8080, queue, riskRepository, alertRepository);
        apiServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            queue.shutdown();
            for (Thread worker : workers) {
                try {
                    worker.join(2_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            apiServer.close();
        }, "finguard-shutdown"));

        System.out.println("FinGuard is operational: http://127.0.0.1:8080/");
        new CountDownLatch(1).await();
    }
}
