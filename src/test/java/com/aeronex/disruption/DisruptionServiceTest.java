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

import com.aeronex.aircraft.Aircraft;
import com.aeronex.aircraft.AircraftStatus;
import com.aeronex.aircraft.AircraftStatusTransitionService;
import com.aeronex.aircraft.AircraftTransitionSource;
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

    @Mock
    private AircraftStatusTransitionService aircraftStatusTransitionService;

    private DisruptionService disruptionService;

    private final UUID flightId = UUID.randomUUID();

    private Airport airport(String iataCode) {
        return new Airport(iataCode, null, iataCode + " Airport", "City", "Country", "UTC",
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private Flight flightWithStatus(FlightStatus status) {
        return flightWithStatusAndAircraft(status, null);
    }

    private Flight flightWithStatusAndAircraft(FlightStatus status, Aircraft aircraft) {
        Flight flight = new Flight("AA100", airport("JFK"), airport("LAX"), aircraft,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"), OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                null, null, status);
        ReflectionTestUtils.setField(flight, "id", flightId);
        return flight;
    }

    private Aircraft aircraftWithStatus(AircraftStatus status) {
        Aircraft aircraft = new Aircraft("N12345", "Boeing", "737-800", 189, status);
        ReflectionTestUtils.setField(aircraft, "id", UUID.randomUUID());
        return aircraft;
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
                flightStatusTransitionService, aircraftStatusTransitionService);
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
        verifyNoInteractions(aircraftStatusTransitionService);
    }

    @Test
    void createAutoDelaysFlightWhenHighSeverityAndEligible() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.SCHEDULED)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.HIGH));

        verify(flightStatusTransitionService).apply(any(Flight.class), eq(FlightStatus.DELAYED),
                eq(TransitionSource.DISRUPTION_AUTO), anyString());
        // requestWithSeverity uses MECHANICAL, but this flight has no assigned
        // aircraft, so the independent aircraft-maintenance rule must not fire.
        verifyNoInteractions(aircraftStatusTransitionService);
    }

    @Test
    void createAutoDelaysFlightWhenCriticalSeverityAndEligible() {
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flightWithStatus(FlightStatus.BOARDING)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.CRITICAL));

        verify(flightStatusTransitionService).apply(any(Flight.class), eq(FlightStatus.DELAYED),
                eq(TransitionSource.DISRUPTION_AUTO), anyString());
        verifyNoInteractions(aircraftStatusTransitionService);
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
        verifyNoInteractions(aircraftStatusTransitionService);
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
        verifyNoInteractions(aircraftStatusTransitionService);
    }

    @Test
    void createTransitionsAircraftToMaintenanceForMechanicalDisruptionRegardlessOfSeverity() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.ACTIVE);
        when(flightRepository.findById(flightId))
                .thenReturn(Optional.of(flightWithStatusAndAircraft(FlightStatus.SCHEDULED, aircraft)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        // LOW severity: proves the aircraft rule is severity-independent, unlike the
        // flight-delay rule.
        disruptionService.create(requestWithSeverity(DisruptionSeverity.LOW));

        verify(aircraftStatusTransitionService).apply(eq(aircraft), eq(AircraftStatus.MAINTENANCE),
                eq(AircraftTransitionSource.DISRUPTION_AUTO), anyString());
        // LOW severity must still not delay the flight -- the two rules are independent.
        verifyNoInteractions(flightStatusTransitionService);
    }

    @Test
    void createDoesNotTransitionAircraftForNonMechanicalDisruption() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.ACTIVE);
        when(flightRepository.findById(flightId))
                .thenReturn(Optional.of(flightWithStatusAndAircraft(FlightStatus.SCHEDULED, aircraft)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(validRequest(null, null, null)); // WEATHER type

        verifyNoInteractions(aircraftStatusTransitionService);
    }

    @Test
    void createDoesNotTransitionAircraftWhenNoAircraftAssigned() {
        when(flightRepository.findById(flightId))
                .thenReturn(Optional.of(flightWithStatusAndAircraft(FlightStatus.SCHEDULED, null)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.CRITICAL));

        verifyNoInteractions(aircraftStatusTransitionService);
    }

    @Test
    void createDoesNotTransitionAircraftWhenAlreadyInMaintenance() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.MAINTENANCE);
        when(flightRepository.findById(flightId))
                .thenReturn(Optional.of(flightWithStatusAndAircraft(FlightStatus.SCHEDULED, aircraft)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        // A second MECHANICAL disruption against an aircraft already under
        // maintenance must be a no-op -- idempotent, not an error.
        disruptionService.create(requestWithSeverity(DisruptionSeverity.HIGH));

        verifyNoInteractions(aircraftStatusTransitionService);
    }

    @Test
    void createDoesNotTransitionAircraftWhenOutOfService() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.OUT_OF_SERVICE);
        when(flightRepository.findById(flightId))
                .thenReturn(Optional.of(flightWithStatusAndAircraft(FlightStatus.SCHEDULED, aircraft)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        disruptionService.create(requestWithSeverity(DisruptionSeverity.CRITICAL));

        verifyNoInteractions(aircraftStatusTransitionService);
    }

    @Test
    void createCanDelayFlightAndTransitionAircraftIndependentlyFromTheSameDisruption() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.ACTIVE);
        when(flightRepository.findById(flightId))
                .thenReturn(Optional.of(flightWithStatusAndAircraft(FlightStatus.SCHEDULED, aircraft)));
        when(disruptionRepository.saveAndFlush(any(Disruption.class)))
                .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

        // MECHANICAL + HIGH: eligible for both the flight-delay rule and the
        // aircraft-maintenance rule at once -- they are independent, not coupled.
        disruptionService.create(requestWithSeverity(DisruptionSeverity.HIGH));

        verify(flightStatusTransitionService).apply(any(Flight.class), eq(FlightStatus.DELAYED),
                eq(TransitionSource.DISRUPTION_AUTO), anyString());
        verify(aircraftStatusTransitionService).apply(eq(aircraft), eq(AircraftStatus.MAINTENANCE),
                eq(AircraftTransitionSource.DISRUPTION_AUTO), anyString());
    }
}
