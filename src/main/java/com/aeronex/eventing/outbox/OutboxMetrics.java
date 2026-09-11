package com.aeronex.eventing.outbox;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers outbox observability metrics independently of whether the relay itself is
 * enabled, since the backlog size is a fact about the database, not about the relay's
 * lifecycle (and is arguably most valuable to observe precisely when the relay isn't
 * running).
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        Gauge.builder("aeronex.outbox.backlog.size", outboxEventRepository,
                        OutboxEventRepository::countByPublishedAtIsNull)
                .description("Number of outbox events not yet published to Kafka")
                .register(meterRegistry);
    }
}
