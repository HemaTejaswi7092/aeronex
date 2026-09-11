package com.aeronex.flight;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.aeronex.airport.Airport;
import com.aeronex.airport.AirportRepository;

@DataJpaTest
class FlightRepositoryTest {

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private AirportRepository airportRepository;

    private Airport saveAirport(String iataCode) {
        return airportRepository.saveAndFlush(new Airport(iataCode, null, iataCode + " Airport", "City",
                "Country", "UTC", BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void savesAndFindsFlightWithRelations() {
        Airport jfk = saveAirport("JFK");
        Airport lax = saveAirport("LAX");

        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-06-01T15:00:00Z");

        Flight flight = new Flight("AA100", jfk, lax, null, departure, arrival, null, null, FlightStatus.SCHEDULED);
        Flight saved = flightRepository.saveAndFlush(flight);

        assertThat(saved.getId()).isNotNull();
        assertThat(flightRepository.findById(saved.getId())).isPresent();
        assertThat(flightRepository.findById(saved.getId()).orElseThrow().getOriginAirport().getIataCode())
                .isEqualTo("JFK");
    }

    @Test
    void findsByFlightNumberOrderedByScheduledDeparture() {
        Airport jfk = saveAirport("JFK");
        Airport lax = saveAirport("LAX");

        OffsetDateTime base = OffsetDateTime.parse("2026-06-01T10:00:00Z");

        Flight later = flightRepository.saveAndFlush(new Flight("AA100", jfk, lax, null,
                base.plusDays(1), base.plusDays(1).plusHours(5), null, null, FlightStatus.SCHEDULED));
        Flight earlier = flightRepository.saveAndFlush(new Flight("AA100", jfk, lax, null,
                base, base.plusHours(5), null, null, FlightStatus.SCHEDULED));

        List<Flight> results = flightRepository.findByFlightNumberOrderByScheduledDepartureTimeAsc("AA100");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(earlier.getId());
        assertThat(results.get(1).getId()).isEqualTo(later.getId());
    }

    @Test
    void findsByOriginOrDestinationIataCode() {
        Airport jfk = saveAirport("JFK");
        Airport lax = saveAirport("LAX");
        Airport ord = saveAirport("ORD");

        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");

        flightRepository.saveAndFlush(new Flight("AA100", jfk, lax, null,
                departure, departure.plusHours(5), null, null, FlightStatus.SCHEDULED));
        flightRepository.saveAndFlush(new Flight("AA200", ord, jfk, null,
                departure, departure.plusHours(3), null, null, FlightStatus.SCHEDULED));
        flightRepository.saveAndFlush(new Flight("AA300", ord, lax, null,
                departure, departure.plusHours(4), null, null, FlightStatus.SCHEDULED));

        List<Flight> jfkFlights = flightRepository.findByOriginOrDestinationIataCode("JFK");

        assertThat(jfkFlights).hasSize(2);
    }
}
