package com.aeronex.eventing.idempotency;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class IdempotentEventGuard {

    private final ProcessedEventRepository processedEventRepository;

    public IdempotentEventGuard(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    public boolean alreadyProcessed(UUID eventId, String consumerName) {
        return processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName);
    }

    public void markProcessed(UUID eventId, String consumerName) {
        processedEventRepository.save(new ProcessedEvent(eventId, consumerName, Instant.now()));
    }
}
