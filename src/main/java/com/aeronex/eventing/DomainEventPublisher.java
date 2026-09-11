package com.aeronex.eventing;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aeronex.eventing.outbox.OutboxEvent;
import com.aeronex.eventing.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DomainEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, UUID aggregateId, String eventType, int eventVersion, Object payload) {
        UUID eventId = UUID.randomUUID();
        DomainEventEnvelope<Object> envelope = new DomainEventEnvelope<>(
                eventId, eventType, eventVersion, OffsetDateTime.now(), aggregateId, payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize domain event " + eventType, e);
        }

        OutboxEvent outboxEvent = new OutboxEvent(eventId, topic, aggregateId, eventType, eventVersion,
                json, Instant.now());
        outboxEventRepository.save(outboxEvent);
    }
}
