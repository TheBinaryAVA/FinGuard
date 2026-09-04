package com.fingard.worker;

import com.fingard.engine.DetectionEngine;
import com.fingard.exception.InvalidEventException;
import com.fingard.model.Alert;
import com.fingard.model.Decision;
import com.fingard.model.Event;
import com.fingard.model.RiskResult;
import com.fingard.persistence.Repository;
import com.fingard.queue.EventQueue;
import com.fingard.util.IdGenerator;
import com.fingard.util.Logger;
import java.time.Instant;

public class EventWorker implements Runnable {
    private final String workerName;
    private final EventQueue queue;
    private final DetectionEngine detectionEngine;
    private final Repository<Event> eventRepository;
    private final Repository<RiskResult> riskRepository;
    private final Repository<Alert> alertRepository;
    private final Logger logger;

    public EventWorker(String workerName, EventQueue queue, DetectionEngine detectionEngine,
                       Repository<Event> eventRepository, Repository<RiskResult> riskRepository,
                       Repository<Alert> alertRepository, Logger logger) {
        this.workerName = workerName;
        this.queue = queue;
        this.detectionEngine = detectionEngine;
        this.eventRepository = eventRepository;
        this.riskRepository = riskRepository;
        this.alertRepository = alertRepository;
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.info(workerName, "Worker started.");
        try {
            Event event;
            while ((event = queue.take()) != null) {
                try {
                    process(event);
                } catch (InvalidEventException exception) {
                    logger.warn(workerName, "Rejected invalid event: " + exception.getMessage());
                } catch (RuntimeException exception) {
                    logger.error(workerName, "Event failed and was not retried.", exception);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn(workerName, "Worker interrupted during shutdown.");
        } catch (RuntimeException exception) {
            logger.error(workerName, "Worker stopped after an unrecoverable failure.", exception);
        } finally {
            logger.info(workerName, "Worker stopped.");
        }
    }

    private void process(Event event) {
        logger.info(workerName, "Processing event " + event.getEventId() + ".");
        eventRepository.save(event.getEventId(), event);
        RiskResult result = detectionEngine.detect(event);
        riskRepository.save(result.getEventId(), result);
        if (result.getDecision() == Decision.BLOCK || result.getDecision() == Decision.REVIEW) {
            Alert alert = new Alert(IdGenerator.next("ALT"), event.getAccountId(), event.getEventId(),
                    Instant.now(), result);
            alertRepository.save(alert.getAlertId(), alert);
            logger.warn(workerName, "Risk decision " + result.getDecision() + " for event " + event.getEventId() + ".");
        }
    }
}