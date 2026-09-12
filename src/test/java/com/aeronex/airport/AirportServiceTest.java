package com.aeronex.airport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aeronex.airport.dto.AirportCreateRequest;
import com.aeronex.airport.dto.AirportResponse;
import com.aeronex.airport.exception.AirportNotFoundException;
import com.aeronex.airport.exception.DuplicateAirportCodeException;

@ExtendWith(MockitoExtension.class)
class AirportServiceTest {

    @Mock
    private AirportRepository airportRepository;

    private AirportService airportService;

    private AirportCreateRequest sampleRequest() {
        return new AirportCreateRequest("jfk", "kjfk", "John F. Kennedy International Airport",
                "New York", "United States", "America/New_York",
                new BigDecimal("40.639751"), new BigDecimal("-73.778925"));
    }

    @BeforeEach
    void setUp() {
        airportService = new AirportService(airportRepository);
    }

    @Test
    void createNormalizesCodesToUppercase() {
        when(airportRepository.existsByIataCode("JFK")).thenReturn(false);
        when(airportRepository.existsByIcaoCode("KJFK")).thenReturn(false);
        when(airportRepository.saveAndFlush(any(Airport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AirportResponse response = airportService.create(sampleRequest());

        assertThat(response.iataCode()).isEqualTo("JFK");
        assertThat(response.icaoCode()).isEqualTo("KJFK");
    }

    @Test
    void createThrowsWhenIataCodeAlreadyExists() {
        when(airportRepository.existsByIataCode("JFK")).thenReturn(true);

        assertThatThrownBy(() -> airportService.create(sampleRequest()))
                .isInstanceOf(DuplicateAirportCodeException.class);
    }

    @Test
    void createThrowsWhenIcaoCodeAlreadyExists() {
        when(airportRepository.existsByIataCode("JFK")).thenReturn(false);
        when(airportRepository.existsByIcaoCode("KJFK")).thenReturn(true);

        assertThatThrownBy(() -> airportService.create(sampleRequest()))
                .isInstanceOf(DuplicateAirportCodeException.class);
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(airportRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> airportService.findById(id))
                .isInstanceOf(AirportNotFoundException.class);
    }

    @Test
    void findByIataCodeNormalizesInputBeforeLookup() {
        when(airportRepository.findByIataCode("JFK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> airportService.findByIataCode("jfk"))
                .isInstanceOf(AirportNotFoundException.class);
    }
}
