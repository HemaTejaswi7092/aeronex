package com.aeronex.disruption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.aeronex.airport.Airport;
import com.aeronex.disruption.dto.DisruptionCreateRequest;
import com.aeronex.disruption.dto.DisruptionResponse;
import com.aeronex.disruption.exception.DisruptionNotFoundException;
import com.aeronex.disruption.exception.FlightNotEligibleForDisruptionException;
import com.aeronex.disruption.exception.InvalidDisruptionException;
import com.aeronex.flight.Flight;
import com.aeronex.flight.FlightRepository;
import com.aeronex.flight.FlightStatus;
import com.aeronex.flight.exception.FlightNotFoundException;

@ExtendWith(MockitoExtension.class)
class DisruptionServiceTest {

    @Mock
    private DisruptionRepository disruptionRepository;

    @Mock
    private FlightRepository flightRepository;

    private DisruptionService disruptionService;

    private final UUID flightId = UUID.randomUUID();

    private Airport airport(String iataCode) {
        return new Airport(iataCode, null, iataCode + " Airport", "City", "Country", "UTC",
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private Flight flightWithStatus(FlightStatus status) {
        Flight flight = new Flight("AA100", airport("JFK"), airport("LAX"), null,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"), OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                null, null, status);
        ReflectionTestUtils.setField(flight, "id", flightId);
        return flight;
    }

    private DisruptionCreateRequest validRequest(DisruptionStatus status, OffsetDateTime reportedAt,
                                                  OffsetDateTime resolvedAt) {
        return new DisruptionCreateRequest(flightId, DisruptionType.WEATHER, DisruptionSeverity.MEDIUM,
                "Heavy snow at origin", 45, reportedAt, status, resolvedAt);
    }

    @BeforeEach
    void setUp() {
        disruptionService = new DisruptionService(disruptionRepository, flightRepository);
    }

    @Test
    void createDefaultsReportedAtAndStatusWhenOmitted() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));
        when(disruptionRepository.save(any(Disruption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisruptionResponse response = disruptionService.create(validRequest(null, null, null));

        assertThat(response.status()).isEqualTo(DisruptionStatus.OPEN);
        assertThat(response.reportedAt()).isNotNull();
        assertThat(response.flight().flightNumber()).isEqualTo("AA100");
        assertThat(response.flight().originIataCode()).isEqualTo("JFK");
        assertThat(response.flight().destinationIataCode()).isEqualTo("LAX");
    }

    @Test
    void createThrowsWhenFlightNotFound() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disruptionService.create(validRequest(null, null, null)))
                .isInstanceOf(FlightNotFoundException.class);
    }

    @Test
    void createThrowsWhenFlightArrived() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.ARRIVED)));

        assertThatThrownBy(() -> disruptionService.create(validRequest(null, null, null)))
                .isInstanceOf(FlightNotEligibleForDisruptionException.class);
    }

    @Test
    void createThrowsWhenFlightCancelled() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.CANCELLED)));

        assertThatThrownBy(() -> disruptionService.create(validRequest(null, null, null)))
                .isInstanceOf(FlightNotEligibleForDisruptionException.class);
    }

    @Test
    void createThrowsWhenResolvedAtSetButStatusNotResolved() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));

        OffsetDateTime resolvedAt = OffsetDateTime.now();

        assertThatThrownBy(() -> disruptionService.create(validRequest(DisruptionStatus.OPEN, null, resolvedAt)))
                .isInstanceOf(InvalidDisruptionException.class);
    }

    @Test
    void createAutoSetsResolvedAtWhenStatusResolvedAndResolvedAtOmitted() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));
        when(disruptionRepository.save(any(Disruption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisruptionResponse response = disruptionService.create(
                validRequest(DisruptionStatus.RESOLVED, null, null));

        assertThat(response.status()).isEqualTo(DisruptionStatus.RESOLVED);
        assertThat(response.resolvedAt()).isNotNull();
    }

    @Test
    void createThrowsWhenResolvedAtBeforeReportedAt() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));

        OffsetDateTime reportedAt = OffsetDateTime.parse("2026-06-01T09:00:00Z");
        OffsetDateTime resolvedAt = OffsetDateTime.parse("2026-06-01T08:00:00Z");

        assertThatThrownBy(() -> disruptionService.create(
                validRequest(DisruptionStatus.RESOLVED, reportedAt, resolvedAt)))
                .isInstanceOf(InvalidDisruptionException.class);
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(disruptionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disruptionService.findById(id))
                .isInstanceOf(DisruptionNotFoundException.class);
    }

    @Test
    void resolveThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(disruptionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disruptionService.resolve(id))
                .isInstanceOf(DisruptionNotFoundException.class);
    }

    @Test
    void resolveSetsStatusAndResolvedAt() {
        Flight flight = flightWithStatus(FlightStatus.SCHEDULED);
        Disruption disruption = new Disruption(flight, DisruptionType.WEATHER, DisruptionSeverity.MEDIUM,
                DisruptionStatus.OPEN, "Heavy snow", 30, OffsetDateTime.parse("2026-06-01T09:00:00Z"), null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(disruption, "id", id);

        when(disruptionRepository.findById(id)).thenReturn(Optional.of(disruption));
        when(disruptionRepository.save(any(Disruption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisruptionResponse response = disruptionService.resolve(id);

        assertThat(response.status()).isEqualTo(DisruptionStatus.RESOLVED);
        assertThat(response.resolvedAt()).isNotNull();
    }

    @Test
    void resolveIsIdempotentAndKeepsOriginalResolvedAt() {
        Flight flight = flightWithStatus(FlightStatus.SCHEDULED);
        OffsetDateTime originalResolvedAt = OffsetDateTime.parse("2026-06-01T12:00:00Z");
        Disruption disruption = new Disruption(flight, DisruptionType.WEATHER, DisruptionSeverity.MEDIUM,
                DisruptionStatus.RESOLVED, "Heavy snow", 30, OffsetDateTime.parse("2026-06-01T09:00:00Z"),
                originalResolvedAt);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(disruption, "id", id);

        when(disruptionRepository.findById(id)).thenReturn(Optional.of(disruption));
        when(disruptionRepository.save(any(Disruption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisruptionResponse response = disruptionService.resolve(id);

        assertThat(response.resolvedAt()).isEqualTo(originalResolvedAt);
    }
}
