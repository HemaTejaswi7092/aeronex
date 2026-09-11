package com.aeronex.disruption;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.aeronex.airport.Airport;
import com.aeronex.airport.AirportRepository;
import com.aeronex.flight.Flight;
import com.aeronex.flight.FlightRepository;
import com.aeronex.flight.FlightStatus;

@DataJpaTest
class DisruptionRepositoryTest {

    @Autowired
    private DisruptionRepository disruptionRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private AirportRepository airportRepository;

    private Flight saveFlight() {
        Airport jfk = airportRepository.saveAndFlush(new Airport("JFK", null, "JFK Airport", "City", "Country",
                "UTC", BigDecimal.ZERO, BigDecimal.ZERO));
        Airport lax = airportRepository.saveAndFlush(new Airport("LAX", null, "LAX Airport", "City", "Country",
                "UTC", BigDecimal.ZERO, BigDecimal.ZERO));
        return flightRepository.saveAndFlush(new Flight("AA100", jfk, lax, null,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"), OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                null, null, FlightStatus.SCHEDULED));
    }

    @Test
    void savesAndFindsDisruptionWithFlight() {
        Flight flight = saveFlight();
        Disruption disruption = new Disruption(flight, DisruptionType.WEATHER, DisruptionSeverity.MEDIUM,
                DisruptionStatus.OPEN, "Heavy snow", 30, OffsetDateTime.parse("2026-06-01T08:00:00Z"), null);

        Disruption saved = disruptionRepository.saveAndFlush(disruption);

        assertThat(saved.getId()).isNotNull();
        assertThat(disruptionRepository.findById(saved.getId())).isPresent();
        assertThat(disruptionRepository.findById(saved.getId()).orElseThrow().getFlight().getFlightNumber())
                .isEqualTo("AA100");
    }

    @Test
    void findsByFlightIdOrderedByReportedAtDescending() {
        Flight flight = saveFlight();

        Disruption earlier = disruptionRepository.saveAndFlush(new Disruption(flight, DisruptionType.WEATHER,
                DisruptionSeverity.LOW, DisruptionStatus.OPEN, "Light delay", 10,
                OffsetDateTime.parse("2026-06-01T07:00:00Z"), null));
        Disruption later = disruptionRepository.saveAndFlush(new Disruption(flight, DisruptionType.MECHANICAL,
                DisruptionSeverity.HIGH, DisruptionStatus.OPEN, "Engine issue", 90,
                OffsetDateTime.parse("2026-06-01T09:00:00Z"), null));

        List<Disruption> results = disruptionRepository.findByFlightIdOrderByReportedAtDesc(flight.getId());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(later.getId());
        assertThat(results.get(1).getId()).isEqualTo(earlier.getId());
    }

    @Test
    void findsActiveDisruptionsExcludingResolved() {
        Flight flight = saveFlight();

        disruptionRepository.saveAndFlush(new Disruption(flight, DisruptionType.WEATHER, DisruptionSeverity.LOW,
                DisruptionStatus.OPEN, "Open one", 10, OffsetDateTime.parse("2026-06-01T07:00:00Z"), null));
        disruptionRepository.saveAndFlush(new Disruption(flight, DisruptionType.CREW, DisruptionSeverity.MEDIUM,
                DisruptionStatus.MONITORING, "Monitoring one", 20, OffsetDateTime.parse("2026-06-01T08:00:00Z"),
                null));
        disruptionRepository.saveAndFlush(new Disruption(flight, DisruptionType.SECURITY, DisruptionSeverity.HIGH,
                DisruptionStatus.RESOLVED, "Resolved one", 5, OffsetDateTime.parse("2026-06-01T06:00:00Z"),
                OffsetDateTime.parse("2026-06-01T06:30:00Z")));

        List<Disruption> active = disruptionRepository.findByStatusInOrderByReportedAtDesc(
                List.of(DisruptionStatus.OPEN, DisruptionStatus.MONITORING));

        assertThat(active).hasSize(2);
        assertThat(active).extracting(Disruption::getStatus)
                .containsExactlyInAnyOrder(DisruptionStatus.OPEN, DisruptionStatus.MONITORING);
    }
}
