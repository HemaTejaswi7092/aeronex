package com.aeronex.disruption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.aeronex.eventing.DomainEventPublisher;
import com.aeronex.eventing.kafka.KafkaTopics;
import com.aeronex.flight.Flight;
import com.aeronex.flight.FlightRepository;
import com.aeronex.flight.FlightStatus;
import com.aeronex.flight.FlightStatusTransitionService;
import com.aeronex.flight.TransitionSource;
import com.aeronex.flight.exception.FlightNotFoundException;

@ExtendWith(MockitoExtension.class)
class DisruptionServiceTest {

    @Mock
    private DisruptionRepository disruptionRepository;

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private FlightStatusTransitionService flightStatusTransitionService;

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

    private DisruptionCreateRequest requestWithSeverity(DisruptionSeverity severity) {
        return new DisruptionCreateRequest(flightId, DisruptionType.MECHANICAL, severity,
                "Engine inspection required", null, null, null, null);
    }

    private Disruption withGeneratedId(Disruption disruption) {
        ReflectionTestUtils.setField(disruption, "id", UUID.randomUUID());
        return disruption;
    }

    @BeforeEach
    void setUp() {
        disruptionService = new DisruptionService(disruptionRepository, flightRepository, domainEventPublisher,
                flightStatusTransitionService);
    }

    @Test
    void createDefaultsReportedAtAndStatusWhenOmitted() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        DisruptionResponse response = disruptionService.create(validRequest(null, null, null));

        assertThat(response.status()).isEqualTo(DisruptionStatus.OPEN);
        assertThat(response.reportedAt()).isNotNull();
        assertThat(response.flight().flightNumber()).isEqualTo("AA100");
        assertThat(response.flight().originIataCode()).isEqualTo("JFK");
        assertThat(response.flight().destinationIataCode()).isEqualTo("LAX");
        verify(domainEventPublisher).publish(eq(KafkaTopics.DISRUPTION_REPORTED), any(UUID.class),
                eq("FlightDisruptionReported"), eq(1), any());
        verify(domainEventPublisher, never()).publish(eq(KafkaTopics.DISRUPTION_RESOLVED), any(UUID.class),
                anyString(), anyInt(), any());
        verifyNoInteractions(flightStatusTransitionService);
    }

    @Test
    void createAutoDelaysFlightWhenHighSeverityAndEligible() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.HIGH));

        verify(flightStatusTransitionService).apply(any(Flight.class), eq(FlightStatus.DELAYED),
                eq(TransitionSource.DISRUPTION_AUTO), anyString());
    }

    @Test
    void createAutoDelaysFlightWhenCriticalSeverityAndEligible() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.BOARDING)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.CRITICAL));

        verify(flightStatusTransitionService).apply(any(Flight.class), eq(FlightStatus.DELAYED),
                eq(TransitionSource.DISRUPTION_AUTO), anyString());
    }

    @Test
    void createDoesNotAutoDelayForLowSeverity() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.LOW));

        verifyNoInteractions(flightStatusTransitionService);
    }

    @Test
    void createDoesNotAutoDelayForMediumSeverity() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.MEDIUM));

        verifyNoInteractions(flightStatusTransitionService);
    }

    @Test
    void createDoesNotAutoDelayWhenFlightAlreadyDelayed() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.DELAYED)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.HIGH));

        verifyNoInteractions(flightStatusTransitionService);
    }

    @Test
    void createDoesNotAutoDelayWhenFlightAlreadyDeparted() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.DEPARTED)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.CRITICAL));

        verifyNoInteractions(flightStatusTransitionService);
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
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        DisruptionResponse response = disruptionService.create(
                validRequest(DisruptionStatus.RESOLVED, null, null));

        assertThat(response.status()).isEqualTo(DisruptionStatus.RESOLVED);
        assertThat(response.resolvedAt()).isNotNull();
        verify(domainEventPublisher).publish(eq(KafkaTopics.DISRUPTION_REPORTED), any(UUID.class),
                eq("FlightDisruptionReported"), eq(1), any());
        verify(domainEventPublisher).publish(eq(KafkaTopics.DISRUPTION_RESOLVED), any(UUID.class),
                eq("FlightDisruptionResolved"), eq(1), any());
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
        when(disruptionRepository.saveAndFlush(any(Disruption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisruptionResponse response = disruptionService.resolve(id);

        assertThat(response.status()).isEqualTo(DisruptionStatus.RESOLVED);
        assertThat(response.resolvedAt()).isNotNull();
        verify(domainEventPublisher, times(1)).publish(eq(KafkaTopics.DISRUPTION_RESOLVED), any(UUID.class),
                eq("FlightDisruptionResolved"), eq(1), any());
        verifyNoInteractions(flightStatusTransitionService);
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
        when(disruptionRepository.saveAndFlush(any(Disruption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisruptionResponse response = disruptionService.resolve(id);

        assertThat(response.resolvedAt()).isEqualTo(originalResolvedAt);
        verify(domainEventPublisher, never()).publish(eq(KafkaTopics.DISRUPTION_RESOLVED), any(UUID.class),
                anyString(), anyInt(), any());
        verifyNoInteractions(flightStatusTransitionService);
    }
}
