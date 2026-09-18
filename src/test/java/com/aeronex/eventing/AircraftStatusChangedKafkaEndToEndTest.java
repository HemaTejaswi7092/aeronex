package com.aeronex.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.aeronex.aircraft.Aircraft;
import com.aeronex.aircraft.AircraftRepository;
import com.aeronex.aircraft.AircraftStatus;
import com.aeronex.airport.Airport;
import com.aeronex.airport.AirportRepository;
import com.aeronex.disruption.DisruptionSeverity;
import com.aeronex.disruption.DisruptionType;
import com.aeronex.disruption.DisruptionService;
import com.aeronex.disruption.dto.DisruptionCreateRequest;
import com.aeronex.eventing.kafka.KafkaTopics;
import com.aeronex.flight.Flight;
import com.aeronex.flight.FlightRepository;
import com.aeronex.flight.FlightStatus;

/**
 * Proves AircraftStatusChanged actually reaches Kafka through the same outbox path as the
 * disruption and flight-status events in the sibling E2E test classes. Kept in its own
 * isolated test class with its own H2 database name and embedded broker for the same
 * reason as FlightStatusChangedKafkaEndToEndTest: shared background relay/listener
 * threads and a shared MeterRegistry make a second disruption-creating test in someone
 * else's class racy.
 *
 * AircraftStatusChanged has no consumer in this codebase (by design — see
 * AircraftStatusTransitionService), so this proves the event reaches Kafka the only way
 * possible: consuming it directly from the embedded broker.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aeronex-kafka-e2e-aircraft-status;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "aeronex.outbox.relay.enabled=true"
})
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.AIRCRAFT_STATUS_CHANGED})
class AircraftStatusChangedKafkaEndToEndTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private DisruptionService disruptionService;

    @Test
    void aircraftStatusChangedEventReachesKafkaForMechanicalDisruption() {
        Airport jfk = airportRepository.saveAndFlush(new Airport("JFK", null,
                "John F. Kennedy International Airport", "New York", "United States", "America/New_York",
                new BigDecimal("40.639751"), new BigDecimal("-73.778925")));
        Airport lax = airportRepository.saveAndFlush(new Airport("LAX", null,
                "Los Angeles International Airport", "Los Angeles", "United States", "America/Los_Angeles",
                new BigDecimal("33.941589"), new BigDecimal("-118.408530")));

        Aircraft aircraft = aircraftRepository.saveAndFlush(
                new Aircraft("N77777", "Boeing", "737-800", 189, AircraftStatus.ACTIVE));

        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-06-01T13:00:00Z");
        Flight flight = flightRepository.saveAndFlush(new Flight("AA400", jfk, lax, aircraft,
                departure, arrival, null, null, FlightStatus.SCHEDULED));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("aircraft-status-changed-test",
                "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps)
                .createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.AIRCRAFT_STATUS_CHANGED);

            // LOW severity: proves the aircraft rule fires regardless of severity.
            DisruptionCreateRequest request = new DisruptionCreateRequest(flight.getId(), DisruptionType.MECHANICAL,
                    DisruptionSeverity.LOW, "Engine inspection required", null, null, null, null);
            disruptionService.create(request);

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer,
                    KafkaTopics.AIRCRAFT_STATUS_CHANGED, Duration.ofSeconds(15));

            assertThat(record.key()).isEqualTo(aircraft.getId().toString());
            assertThat(record.value()).contains("\"eventType\":\"AircraftStatusChanged\"");
            assertThat(record.value()).contains("\"newStatus\":\"MAINTENANCE\"");
            assertThat(record.value()).contains("\"source\":\"DISRUPTION_AUTO\"");
        }
    }
}
