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
 * Proves FlightStatusChanged actually reaches Kafka through the same outbox path as the
 * disruption events in DisruptionKafkaEndToEndTest. Kept in its own test class with its
 * own isolated H2 database name and embedded broker rather than added as a second method
 * there: that class's background outbox relay/listener threads run for the whole class
 * lifetime and share one MeterRegistry with no reset between methods, so a second
 * disruption-creating test in the same class would race its own counter assertions.
 *
 * FlightStatusChanged has no consumer in this codebase (by design — see
 * FlightStatusTransitionService), so unlike the disruption events there is no downstream
 * audit-table side effect to assert on. This proves the event reaches Kafka the only way
 * possible: consuming it directly from the embedded broker.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aeronex-kafka-e2e-flight-status;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "aeronex.outbox.relay.enabled=true"
})
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.FLIGHT_STATUS_CHANGED})
class FlightStatusChangedKafkaEndToEndTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private DisruptionService disruptionService;

    @Test
    void flightStatusChangedEventReachesKafkaForQualifyingDisruption() {
        Airport jfk = airportRepository.saveAndFlush(new Airport("JFK", null,
                "John F. Kennedy International Airport", "New York", "United States", "America/New_York",
                new BigDecimal("40.639751"), new BigDecimal("-73.778925")));
        Airport lax = airportRepository.saveAndFlush(new Airport("LAX", null,
                "Los Angeles International Airport", "Los Angeles", "United States", "America/Los_Angeles",
                new BigDecimal("33.941589"), new BigDecimal("-118.408530")));

        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-06-01T13:00:00Z");
        Flight flight = flightRepository.saveAndFlush(new Flight("AA200", jfk, lax, null,
                departure, arrival, null, null, FlightStatus.SCHEDULED));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("flight-status-changed-test",
                "true", embeddedKafkaBroker);
        // KafkaTestUtils.consumerProps defaults to IntegerDeserializer for the key; the
        // app's producer (KafkaConfig.producerFactory) sends String keys/values, so both
        // must be overridden explicitly here.
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps)
                .createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.FLIGHT_STATUS_CHANGED);

            DisruptionCreateRequest request = new DisruptionCreateRequest(flight.getId(), DisruptionType.MECHANICAL,
                    DisruptionSeverity.HIGH, "Engine inspection required", null, null, null, null);
            disruptionService.create(request);

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer,
                    KafkaTopics.FLIGHT_STATUS_CHANGED, Duration.ofSeconds(15));

            assertThat(record.key()).isEqualTo(flight.getId().toString());
            assertThat(record.value()).contains("\"eventType\":\"FlightStatusChanged\"");
            assertThat(record.value()).contains("\"newStatus\":\"DELAYED\"");
            assertThat(record.value()).contains("\"source\":\"DISRUPTION_AUTO\"");
        }
    }
}
