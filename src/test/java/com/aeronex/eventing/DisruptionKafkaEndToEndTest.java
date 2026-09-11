package com.aeronex.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import com.aeronex.airport.Airport;
import com.aeronex.airport.AirportRepository;
import com.aeronex.disruption.DisruptionEventLog;
import com.aeronex.disruption.DisruptionEventLogRepository;
import com.aeronex.disruption.DisruptionSeverity;
import com.aeronex.disruption.DisruptionType;
import com.aeronex.disruption.DisruptionService;
import com.aeronex.disruption.dto.DisruptionCreateRequest;
import com.aeronex.disruption.dto.DisruptionResponse;
import com.aeronex.eventing.kafka.KafkaHealthIndicator;
import com.aeronex.eventing.kafka.KafkaTopics;
import com.aeronex.eventing.outbox.OutboxEventRepository;
import com.aeronex.flight.Flight;
import com.aeronex.flight.FlightRepository;
import com.aeronex.flight.FlightStatus;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Proves the real end-to-end path: DisruptionService writes an outbox row in the same
 * transaction as the domain write, OutboxRelay publishes it to an embedded Kafka broker,
 * and DisruptionEventListener consumes it into the DisruptionEventLog audit table.
 * Uses its own isolated H2 database name so it never shares state with the rest of the
 * suite's transactional tests, since this test relies on real commits visible across
 * the relay's and listener's background threads.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aeronex-kafka-e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "aeronex.outbox.relay.enabled=true"
})
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.DISRUPTION_REPORTED, KafkaTopics.DISRUPTION_RESOLVED})
class DisruptionKafkaEndToEndTest {

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private DisruptionService disruptionService;

    @Autowired
    private DisruptionEventLogRepository disruptionEventLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private KafkaHealthIndicator kafkaHealthIndicator;

    @Test
    void disruptionReportedAndResolvedFlowThroughKafkaToTheAuditLog() throws Exception {
        Airport jfk = airportRepository.saveAndFlush(new Airport("JFK", null,
                "John F. Kennedy International Airport", "New York", "United States", "America/New_York",
                new BigDecimal("40.639751"), new BigDecimal("-73.778925")));
        Airport lax = airportRepository.saveAndFlush(new Airport("LAX", null,
                "Los Angeles International Airport", "Los Angeles", "United States", "America/Los_Angeles",
                new BigDecimal("33.941589"), new BigDecimal("-118.408530")));

        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-06-01T13:00:00Z");
        Flight flight = flightRepository.saveAndFlush(new Flight("AA100", jfk, lax, null,
                departure, arrival, null, null, FlightStatus.SCHEDULED));

        DisruptionCreateRequest request = new DisruptionCreateRequest(flight.getId(), DisruptionType.WEATHER,
                DisruptionSeverity.MEDIUM, "Heavy snow at origin airport", 45, null, null, null);
        DisruptionResponse created = disruptionService.create(request);
        UUID disruptionId = created.id();

        List<DisruptionEventLog> afterReported = awaitLogEntries(disruptionId, 1);
        assertThat(afterReported).hasSize(1);
        assertThat(afterReported.get(0).getEventType()).isEqualTo("FlightDisruptionReported");
        assertThat(afterReported.get(0).getFlightId()).isEqualTo(flight.getId());
        assertThat(afterReported.get(0).getSummary()).contains("AA100");

        disruptionService.resolve(disruptionId);

        List<DisruptionEventLog> afterResolved = awaitLogEntries(disruptionId, 2);
        assertThat(afterResolved).hasSize(2);
        assertThat(afterResolved.get(1).getEventType()).isEqualTo("FlightDisruptionResolved");

        awaitAllOutboxEventsPublished();

        assertThat(meterRegistry.counter("aeronex.disruption.events.processed",
                "eventType", "FlightDisruptionReported").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("aeronex.disruption.events.processed",
                "eventType", "FlightDisruptionResolved").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("aeronex.outbox.publish",
                "topic", KafkaTopics.DISRUPTION_REPORTED, "result", "success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("aeronex.outbox.publish",
                "topic", KafkaTopics.DISRUPTION_RESOLVED, "result", "success").count()).isEqualTo(1.0);

        assertThat(kafkaHealthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    private List<DisruptionEventLog> awaitLogEntries(UUID disruptionId, int expectedCount) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        List<DisruptionEventLog> entries;
        do {
            entries = disruptionEventLogRepository.findByDisruptionIdOrderByOccurredAtAsc(disruptionId);
            if (entries.size() >= expectedCount) {
                return entries;
            }
            Thread.sleep(200);
        } while (System.currentTimeMillis() < deadline);
        return entries;
    }

    private void awaitAllOutboxEventsPublished() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().isEmpty()) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).isEmpty();
    }
}
