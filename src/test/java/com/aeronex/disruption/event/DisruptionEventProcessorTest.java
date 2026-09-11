package com.aeronex.disruption.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aeronex.disruption.DisruptionEventLog;
import com.aeronex.disruption.DisruptionEventLogRepository;
import com.aeronex.eventing.idempotency.IdempotentEventGuard;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DisruptionEventProcessorTest {

    @Mock
    private IdempotentEventGuard idempotentEventGuard;

    @Mock
    private DisruptionEventLogRepository disruptionEventLogRepository;

    private DisruptionEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DisruptionEventProcessor(idempotentEventGuard, disruptionEventLogRepository,
                new ObjectMapper().findAndRegisterModules());
    }

    private String envelopeJson(UUID eventId, UUID disruptionId, UUID flightId, String eventType) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "payload": {
                    "disruptionId": "%s",
                    "flightId": "%s",
                    "flightNumber": "AA100",
                    "disruptionType": "WEATHER",
                    "severity": "MEDIUM"
                  }
                }
                """.formatted(eventId, eventType, OffsetDateTime.now(), disruptionId, disruptionId, flightId);
    }

    @Test
    void processesNewEventAndRecordsLogAndMarksProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID disruptionId = UUID.randomUUID();
        UUID flightId = UUID.randomUUID();
        when(idempotentEventGuard.alreadyProcessed(eventId, DisruptionEventProcessor.CONSUMER_NAME)).thenReturn(false);

        processor.process(envelopeJson(eventId, disruptionId, flightId, "FlightDisruptionReported"));

        ArgumentCaptor<DisruptionEventLog> captor = ArgumentCaptor.forClass(DisruptionEventLog.class);
        verify(disruptionEventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(captor.getValue().getDisruptionId()).isEqualTo(disruptionId);
        assertThat(captor.getValue().getFlightId()).isEqualTo(flightId);
        assertThat(captor.getValue().getSummary()).contains("AA100");

        verify(idempotentEventGuard).markProcessed(eventId, DisruptionEventProcessor.CONSUMER_NAME);
    }

    @Test
    void skipsAlreadyProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID disruptionId = UUID.randomUUID();
        UUID flightId = UUID.randomUUID();
        when(idempotentEventGuard.alreadyProcessed(eventId, DisruptionEventProcessor.CONSUMER_NAME)).thenReturn(true);

        processor.process(envelopeJson(eventId, disruptionId, flightId, "FlightDisruptionReported"));

        verify(disruptionEventLogRepository, never()).save(any());
        verify(idempotentEventGuard, never()).markProcessed(any(), any());
    }

    @Test
    void throwsOnMalformedPayload() {
        try {
            processor.process("not-json");
        } catch (IllegalArgumentException expected) {
            verify(disruptionEventLogRepository, times(0)).save(any());
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException for malformed payload");
    }
}
