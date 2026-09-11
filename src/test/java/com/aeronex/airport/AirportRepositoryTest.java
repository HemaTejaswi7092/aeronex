package com.aeronex.airport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class AirportRepositoryTest {

    @Autowired
    private AirportRepository airportRepository;

    private Airport jfk() {
        return new Airport("JFK", "KJFK", "John F. Kennedy International Airport",
                "New York", "United States", "America/New_York",
                new BigDecimal("40.639751"), new BigDecimal("-73.778925"));
    }

    @Test
    void savesAndFindsByIataCode() {
        airportRepository.saveAndFlush(jfk());

        assertThat(airportRepository.findByIataCode("JFK")).isPresent();
        assertThat(airportRepository.existsByIataCode("JFK")).isTrue();
        assertThat(airportRepository.existsByIcaoCode("KJFK")).isTrue();
    }

    @Test
    void rejectsDuplicateIataCode() {
        airportRepository.saveAndFlush(jfk());

        Airport duplicate = new Airport("JFK", "KJFX", "Some Other Airport",
                "Elsewhere", "United States", "America/New_York",
                new BigDecimal("1.000000"), new BigDecimal("1.000000"));

        assertThatThrownBy(() -> airportRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateIcaoCode() {
        airportRepository.saveAndFlush(jfk());

        Airport duplicate = new Airport("LAX", "KJFK", "Some Other Airport",
                "Elsewhere", "United States", "America/Los_Angeles",
                new BigDecimal("1.000000"), new BigDecimal("1.000000"));

        assertThatThrownBy(() -> airportRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsMultipleAirportsWithoutIcaoCode() {
        Airport first = new Airport("AAA", null, "Airport One",
                "City One", "Country One", "UTC",
                new BigDecimal("1.000000"), new BigDecimal("1.000000"));
        Airport second = new Airport("BBB", null, "Airport Two",
                "City Two", "Country Two", "UTC",
                new BigDecimal("2.000000"), new BigDecimal("2.000000"));

        airportRepository.saveAndFlush(first);
        airportRepository.saveAndFlush(second);

        assertThat(airportRepository.count()).isEqualTo(2);
    }
}
