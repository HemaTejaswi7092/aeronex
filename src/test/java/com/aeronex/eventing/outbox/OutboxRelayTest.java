package com.aeronex.eventing.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        outboxRelay = new OutboxRelay(outboxEventRepository, kafkaTemplate, meterRegistry);
    }

    private OutboxEvent pendingEvent(String topic) {
        return new OutboxEvent(UUID.randomUUID(), topic, UUID.randomUUID(), "FlightDisruptionReported", 1,
                "{\"eventId\":\"" + UUID.randomUUID() + "\"}", Instant.now());
    }

    @Test
    void publishesPendingEventsAndMarksThemPublished() {
        OutboxEvent event = pendingEvent("aeronex.disruption.reported");
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(eq(event.getTopic()), eq(event.getAggregateId().toString()), eq(event.getPayload())))
                .thenReturn(future);

        outboxRelay.publishPendingEvents();

        verify(kafkaTemplate, times(1)).send(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(meterRegistry.counter("aeronex.outbox.publish", "topic", event.getTopic(), "result", "success")
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("aeronex.outbox.publish").tag("result", "failure").counter()).isNull();
    }

    @Test
    void leavesEventUnpublishedWhenKafkaSendFails() throws Exception {
        OutboxEvent event = pendingEvent("aeronex.disruption.reported");
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> failedFuture = mock(CompletableFuture.class);
        when(failedFuture.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new ExecutionException("broker unavailable", null));
        when(kafkaTemplate.send(eq(event.getTopic()), eq(event.getAggregateId().toString()), eq(event.getPayload())))
                .thenReturn(failedFuture);

        outboxRelay.publishPendingEvents();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(meterRegistry.counter("aeronex.outbox.publish", "topic", event.getTopic(), "result", "failure")
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("aeronex.outbox.publish").tag("result", "success").counter()).isNull();
    }

    @Test
    void doesNothingWhenNoPendingEvents() {
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

        outboxRelay.publishPendingEvents();

        verify(kafkaTemplate, times(0)).send(any(), any(), any());
    }
}
