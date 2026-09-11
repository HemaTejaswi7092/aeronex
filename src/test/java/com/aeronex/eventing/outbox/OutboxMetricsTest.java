package com.aeronex.eventing.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OutboxMetricsTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void backlogGaugeReflectsRepositoryCount() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(outboxEventRepository.countByPublishedAtIsNull()).thenReturn(7L);

        new OutboxMetrics(outboxEventRepository, meterRegistry);

        double backlog = meterRegistry.get("aeronex.outbox.backlog.size").gauge().value();

        assertThat(backlog).isEqualTo(7.0);
    }

    @Test
    void backlogGaugeReflectsZeroWhenNoPendingEvents() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(outboxEventRepository.countByPublishedAtIsNull()).thenReturn(0L);

        new OutboxMetrics(outboxEventRepository, meterRegistry);

        double backlog = meterRegistry.get("aeronex.outbox.backlog.size").gauge().value();

        assertThat(backlog).isEqualTo(0.0);
    }
}
