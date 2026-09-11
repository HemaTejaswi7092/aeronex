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

@Component
public class DisruptionEventProcessor {

    static final String CONSUMER_NAME = "disruption-event-log";

    private final IdempotentEventGuard idempotentEventGuard;
    private final DisruptionEventLogRepository disruptionEventLogRepository;
    private final ObjectMapper objectMapper;

    public DisruptionEventProcessor(IdempotentEventGuard idempotentEventGuard,
                                     DisruptionEventLogRepository disruptionEventLogRepository,
                                     ObjectMapper objectMapper) {
        this.idempotentEventGuard = idempotentEventGuard;
        this.disruptionEventLogRepository = disruptionEventLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(String rawEnvelope) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(rawEnvelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed disruption event payload", e);
        }

        UUID eventId = UUID.fromString(envelope.get("eventId").asText());

        if (idempotentEventGuard.alreadyProcessed(eventId, CONSUMER_NAME)) {
            return;
        }

        String eventType = envelope.get("eventType").asText();
        UUID aggregateId = UUID.fromString(envelope.get("aggregateId").asText());
        OffsetDateTime occurredAt = OffsetDateTime.parse(envelope.get("occurredAt").asText());
        JsonNode payloadNode = envelope.get("payload");
        UUID flightId = UUID.fromString(payloadNode.get("flightId").asText());
        String flightNumber = payloadNode.get("flightNumber").asText();

        String summary = buildSummary(eventType, flightNumber, payloadNode);

        DisruptionEventLog logEntry = new DisruptionEventLog(
                UUID.randomUUID(), eventId, aggregateId, flightId, eventType, occurredAt, summary, Instant.now());
        disruptionEventLogRepository.save(logEntry);

        idempotentEventGuard.markProcessed(eventId, CONSUMER_NAME);
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
