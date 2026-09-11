package com.aeronex.eventing.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@ConditionalOnProperty(name = "aeronex.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate,
                        MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.markPublished(Instant.now());
            publishCounter(event.getTopic(), "success").increment();
        } catch (Exception e) {
            log.warn("Failed to publish outbox event {} ({}) to topic {}: {}",
                    event.getId(), event.getEventType(), event.getTopic(), e.getMessage());
            publishCounter(event.getTopic(), "failure").increment();
        }
    }

    private Counter publishCounter(String topic, String result) {
        return Counter.builder("aeronex.outbox.publish")
                .description("Outbox events published to Kafka, by outcome")
                .tag("topic", topic)
                .tag("result", result)
                .register(meterRegistry);
    }
}
