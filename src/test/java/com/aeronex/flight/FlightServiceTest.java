package com.aeronex.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.aeronex.aircraft.Aircraft;
import com.aeronex.aircraft.AircraftRepository;
import com.aeronex.aircraft.AircraftStatus;
import com.aeronex.aircraft.exception.AircraftNotFoundException;
import com.aeronex.airport.Airport;
import com.aeronex.airport.AirportRepository;
import com.aeronex.airport.exception.AirportNotFoundException;
import com.aeronex.flight.dto.FlightCreateRequest;
import com.aeronex.flight.dto.FlightResponse;
import com.aeronex.flight.dto.FlightStatusHistoryResponse;
import com.aeronex.flight.dto.FlightStatusUpdateRequest;
import com.aeronex.flight.exception.AircraftNotAvailableException;
import com.aeronex.flight.exception.FlightNotFoundException;
import com.aeronex.flight.exception.InvalidFlightException;
import com.aeronex.flight.exception.InvalidFlightStatusTransitionException;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private AircraftRepository aircraftRepository;

    @Mock
    private FlightStatusHistoryRepository flightStatusHistoryRepository;

    @Mock
    private FlightStatusTransitionService flightStatusTransitionService;

    private FlightService flightService;

    private final UUID originId = UUID.randomUUID();
    private final UUID destinationId = UUID.randomUUID();
    private final UUID aircraftId = UUID.randomUUID();

    private Airport airportWithId(UUID id, String iataCode) {
        Airport airport = new Airport(iataCode, null, iataCode + " Airport", "City", "Country", "UTC",
                BigDecimal.ZERO, BigDecimal.ZERO);
        ReflectionTestUtils.setField(airport, "id", id);
        return airport;
    }

    private Aircraft aircraftWithId(UUID id, AircraftStatus status) {
        Aircraft aircraft = new Aircraft("N12345", "Boeing", "737-800", 189, status);
        ReflectionTestUtils.setField(aircraft, "id", id);
        return aircraft;
    }

    private FlightCreateRequest validRequest(UUID origin, UUID destination, UUID aircraft) {
        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-06-01T13:00:00Z");
        return new FlightCreateRequest("aa100", origin, destination, aircraft, departure, arrival, null, null, null);
    }

    private Flight flightWithId(UUID id, FlightStatus status) {
        Flight flight = new Flight("AA100", airportWithId(originId, "JFK"), airportWithId(destinationId, "LAX"),
                null, OffsetDateTime.parse("2026-06-01T10:00:00Z"), OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                null, null, status);
        ReflectionTestUtils.setField(flight, "id", id);
        return flight;
    }

    @BeforeEach
    void setUp() {
        flightService = new FlightService(flightRepository, airportRepository, aircraftRepository,
                flightStatusHistoryRepository, flightStatusTransitionService);
    }

    @Test
    void createNormalizesFlightNumberAndDefaultsStatus() {
        when(airportRepository.findById(originId)).thenReturn(Optional.of(airportWithId(originId, "JFK")));
        when(airportRepository.findById(destinationId)).thenReturn(Optional.of(airportWithId(destinationId, "LAX")));
        when(flightRepository.saveAndFlush(any(Flight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponse response = flightService.create(validRequest(originId, destinationId, null));

        assertThat(response.flightNumber()).isEqualTo("AA100");
        assertThat(response.status()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(response.originAirport().iataCode()).isEqualTo("JFK");
        assertThat(response.destinationAirport().iataCode()).isEqualTo("LAX");
        assertThat(response.aircraft()).isNull();
        verify(flightStatusTransitionService).seedInitialHistory(any(Flight.class));
    }

    @Test
    void createAssignsAircraftWhenActive() {
        when(airportRepository.findById(originId)).thenReturn(Optional.of(airportWithId(originId, "JFK")));
        when(airportRepository.findById(destinationId)).thenReturn(Optional.of(airportWithId(destinationId, "LAX")));
        when(aircraftRepository.findById(aircraftId))
                .thenReturn(Optional.of(aircraftWithId(aircraftId, AircraftStatus.ACTIVE)));
        when(flightRepository.saveAndFlush(any(Flight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponse response = flightService.create(validRequest(originId, destinationId, aircraftId));

        assertThat(response.aircraft()).isNotNull();
        assertThat(response.aircraft().registrationNumber()).isEqualTo("N12345");
    }

    @Test
    void createThrowsWhenOriginAndDestinationAreTheSame() {
        when(airportRepository.findById(originId)).thenReturn(Optional.of(airportWithId(originId, "JFK")));

        assertThatThrownBy(() -> flightService.create(validRequest(originId, originId, null)))
                .isInstanceOf(InvalidFlightException.class);
    }

    @Test
    void createThrowsWhenScheduledArrivalNotAfterDeparture() {
        when(airportRepository.findById(originId)).thenReturn(Optional.of(airportWithId(originId, "JFK")));
        when(airportRepository.findById(destinationId)).thenReturn(Optional.of(airportWithId(destinationId, "LAX")));

        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-06-01T09:00:00Z");
        FlightCreateRequest request = new FlightCreateRequest("AA100", originId, destinationId, null,
                departure, arrival, null, null, null);

        assertThatThrownBy(() -> flightService.create(request))
                .isInstanceOf(InvalidFlightException.class);
    }

    @Test
    void createThrowsWhenActualArrivalNotAfterActualDeparture() {
        when(airportRepository.findById(originId)).thenReturn(Optional.of(airportWithId(originId, "JFK")));
        when(airportRepository.findById(destinationId)).thenReturn(Optional.of(airportWithId(destinationId, "LAX")));

        OffsetDateTime departure = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-06-01T13:00:00Z");
        OffsetDateTime actualDeparture = OffsetDateTime.parse("2026-06-01T10:30:00Z");
        OffsetDateTime actualArrival = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        FlightCreateRequest request = new FlightCreateRequest("AA100", originId, destinationId, null,
                departure, arrival, actualDeparture, actualArrival, null);

        assertThatThrownBy(() -> flightService.create(request))
                .isInstanceOf(InvalidFlightException.class);
    }

    @Test
    void createThrowsWhenOriginAirportNotFound() {
        when(airportRepository.findById(originId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> flightService.create(validRequest(originId, destinationId, null)))
                .isInstanceOf(AirportNotFoundException.class);
    }

    @Test
    void createThrowsWhenAircraftNotFound() {
        when(airportRepository.findById(originId)).thenReturn(Optional.of(airportWithId(originId, "JFK")));
        when(airportRepository.findById(destinationId)).thenReturn(Optional.of(airportWithId(destinationId, "LAX")));
        when(aircraftRepository.findById(aircraftId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> flightService.create(validRequest(originId, destinationId, aircraftId)))
                .isInstanceOf(AircraftNotFoundException.class);
    }

    @Test
    void createThrowsWhenAircraftOutOfService() {
        when(airportRepository.findById(originId)).thenReturn(Optional.of(airportWithId(originId, "JFK")));
        when(airportRepository.findById(destinationId)).thenReturn(Optional.of(airportWithId(destinationId, "LAX")));
        when(aircraftRepository.findById(aircraftId))
                .thenReturn(Optional.of(aircraftWithId(aircraftId, AircraftStatus.OUT_OF_SERVICE)));

        assertThatThrownBy(() -> flightService.create(validRequest(originId, destinationId, aircraftId)))
                .isInstanceOf(AircraftNotAvailableException.class);
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> flightService.findById(id))
                .isInstanceOf(FlightNotFoundException.class);
    }

    @Test
    void updateStatusDelegatesToTransitionServiceAndReturnsResult() {
        UUID id = UUID.randomUUID();
        Flight flight = flightWithId(id, FlightStatus.SCHEDULED);
        Flight transitioned = flightWithId(id, FlightStatus.BOARDING);

        when(flightRepository.findById(id)).thenReturn(Optional.of(flight));
        when(flightStatusTransitionService.apply(flight, FlightStatus.BOARDING, TransitionSource.OPERATOR,
                "Gate ready")).thenReturn(transitioned);

        FlightResponse response = flightService.updateStatus(id,
                new FlightStatusUpdateRequest(FlightStatus.BOARDING, "Gate ready"));

        assertThat(response.status()).isEqualTo(FlightStatus.BOARDING);
        verify(flightStatusTransitionService).apply(flight, FlightStatus.BOARDING, TransitionSource.OPERATOR,
                "Gate ready");
    }

    @Test
    void updateStatusThrowsWhenFlightNotFound() {
        UUID id = UUID.randomUUID();
        when(flightRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> flightService.updateStatus(id,
                new FlightStatusUpdateRequest(FlightStatus.BOARDING, null)))
                .isInstanceOf(FlightNotFoundException.class);

        verifyNoInteractions(flightStatusTransitionService);
    }

    @Test
    void updateStatusPropagatesInvalidTransitionFromTransitionService() {
        UUID id = UUID.randomUUID();
        Flight flight = flightWithId(id, FlightStatus.ARRIVED);

        when(flightRepository.findById(id)).thenReturn(Optional.of(flight));
        when(flightStatusTransitionService.apply(eq(flight), eq(FlightStatus.BOARDING),
                eq(TransitionSource.OPERATOR), any()))
                .thenThrow(new InvalidFlightStatusTransitionException("Cannot transition flight AA100 from ARRIVED to BOARDING"));

        assertThatThrownBy(() -> flightService.updateStatus(id,
                new FlightStatusUpdateRequest(FlightStatus.BOARDING, null)))
                .isInstanceOf(InvalidFlightStatusTransitionException.class);
    }

    @Test
    void getStatusHistoryReturnsChronologicalList() {
        UUID id = UUID.randomUUID();
        Flight flight = flightWithId(id, FlightStatus.BOARDING);
        FlightStatusHistory first = new FlightStatusHistory(flight, null, FlightStatus.SCHEDULED,
                TransitionSource.CREATED, null, OffsetDateTime.parse("2026-06-01T08:00:00Z"));
        FlightStatusHistory second = new FlightStatusHistory(flight, FlightStatus.SCHEDULED, FlightStatus.BOARDING,
                TransitionSource.OPERATOR, "Gate ready", OffsetDateTime.parse("2026-06-01T09:00:00Z"));

        when(flightRepository.existsById(id)).thenReturn(true);
        when(flightStatusHistoryRepository.findByFlightIdOrderByChangedAtAsc(id))
                .thenReturn(List.of(first, second));

        List<FlightStatusHistoryResponse> history = flightService.getStatusHistory(id);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).previousStatus()).isNull();
        assertThat(history.get(0).newStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(history.get(0).source()).isEqualTo(TransitionSource.CREATED);
        assertThat(history.get(1).previousStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(history.get(1).newStatus()).isEqualTo(FlightStatus.BOARDING);
        assertThat(history.get(1).source()).isEqualTo(TransitionSource.OPERATOR);
        assertThat(history.get(1).reason()).isEqualTo("Gate ready");
    }

    @Test
    void getStatusHistoryThrowsWhenFlightNotFound() {
        UUID id = UUID.randomUUID();
        when(flightRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> flightService.getStatusHistory(id))
                .isInstanceOf(FlightNotFoundException.class);
    }
}
