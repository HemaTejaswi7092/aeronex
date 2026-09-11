package com.aeronex.disruption.event;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.disruption.DisruptionEventLog;
import com.aeronex.disruption.DisruptionEventLogRepository;
import com.aeronex.eventing.idempotency.IdempotentEventGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class DisruptionEventProcessor {

    static final String CONSUMER_NAME = "disruption-event-log";

    private static final String UNKNOWN_EVENT_TYPE = "unknown";

    private final IdempotentEventGuard idempotentEventGuard;
    private final DisruptionEventLogRepository disruptionEventLogRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public DisruptionEventProcessor(IdempotentEventGuard idempotentEventGuard,
                                     DisruptionEventLogRepository disruptionEventLogRepository,
                                     ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.idempotentEventGuard = idempotentEventGuard;
        this.disruptionEventLogRepository = disruptionEventLogRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void process(String rawEnvelope) {
        String eventType = UNKNOWN_EVENT_TYPE;
        try {
            JsonNode envelope;
            try {
                envelope = objectMapper.readTree(rawEnvelope);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Malformed disruption event payload", e);
            }

            UUID eventId = UUID.fromString(envelope.get("eventId").asText());
            eventType = envelope.get("eventType").asText();

            if (idempotentEventGuard.alreadyProcessed(eventId, CONSUMER_NAME)) {
                counter("aeronex.disruption.events.duplicate", eventType).increment();
                return;
            }

            UUID aggregateId = UUID.fromString(envelope.get("aggregateId").asText());
            OffsetDateTime occurredAt = OffsetDateTime.parse(envelope.get("occurredAt").asText());
            JsonNode payloadNode = envelope.get("payload");
            UUID flightId = UUID.fromString(payloadNode.get("flightId").asText());
            String flightNumber = payloadNode.get("flightNumber").asText();

            String summary = buildSummary(eventType, flightNumber, payloadNode);

            DisruptionEventLog logEntry = new DisruptionEventLog(
                    UUID.randomUUID(), eventId, aggregateId, flightId, eventType, occurredAt, summary,
                    Instant.now());
            disruptionEventLogRepository.save(logEntry);

            idempotentEventGuard.markProcessed(eventId, CONSUMER_NAME);

            counter("aeronex.disruption.events.processed", eventType).increment();
        } catch (RuntimeException e) {
            counter("aeronex.disruption.events.failed", eventType).increment();
            throw e;
        }
    }

    private Counter counter(String name, String eventType) {
        return Counter.builder(name)
                .tag("eventType", eventType)
                .register(meterRegistry);
    }

    private String buildSummary(String eventType, String flightNumber, JsonNode payloadNode) {
        if ("FlightDisruptionReported".equals(eventType)) {
            return "Disruption reported for flight " + flightNumber + " (" + payloadNode.get("disruptionType").asText()
                    + ", severity " + payloadNode.get("severity").asText() + ")";
        }
        if ("FlightDisruptionResolved".equals(eventType)) {
            return "Disruption resolved for flight " + flightNumber;
        }
        return "Disruption event " + eventType + " for flight " + flightNumber;
    }
}
