package com.aeronex.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aeronex.airport.Airport;
import com.aeronex.eventing.DomainEventPublisher;
import com.aeronex.eventing.kafka.KafkaTopics;
import com.aeronex.flight.exception.InvalidFlightStatusTransitionException;

@ExtendWith(MockitoExtension.class)
class FlightStatusTransitionServiceTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightStatusHistoryRepository flightStatusHistoryRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private FlightStatusTransitionService flightStatusTransitionService;

    private Flight flightWithStatus(FlightStatus status) {
        Airport origin = new Airport("JFK", null, "JFK Airport", "New York", "USA", "America/New_York",
                BigDecimal.ZERO, BigDecimal.ZERO);
        Airport destination = new Airport("LAX", null, "LAX Airport", "Los Angeles", "USA", "America/Los_Angeles",
                BigDecimal.ZERO, BigDecimal.ZERO);
        return new Flight("AA100", origin, destination, null,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"), OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                null, null, status);
    }

    @BeforeEach
    void setUp() {
        flightStatusTransitionService = new FlightStatusTransitionService(flightRepository,
                flightStatusHistoryRepository, domainEventPublisher);
    }

    @Test
    void applySavesFlightRecordsHistoryAndPublishesEvent() {
        Flight flight = flightWithStatus(FlightStatus.SCHEDULED);
        when(flightRepository.saveAndFlush(flight)).thenReturn(flight);

        Flight result = flightStatusTransitionService.apply(flight, FlightStatus.BOARDING,
                TransitionSource.OPERATOR, "Gate ready");

        assertThat(result.getStatus()).isEqualTo(FlightStatus.BOARDING);
        verify(flightRepository).saveAndFlush(flight);
        verify(flightStatusHistoryRepository).saveAndFlush(any(FlightStatusHistory.class));
        verify(domainEventPublisher).publish(eq(KafkaTopics.FLIGHT_STATUS_CHANGED), any(), eq("FlightStatusChanged"),
                eq(1), any());
    }

    @Test
    void applyRecordsCorrectPreviousAndNewStatusInHistory() {
        Flight flight = flightWithStatus(FlightStatus.SCHEDULED);
        when(flightRepository.saveAndFlush(flight)).thenReturn(flight);

        flightStatusTransitionService.apply(flight, FlightStatus.DELAYED, TransitionSource.DISRUPTION_AUTO,
                "Auto-delayed due to HIGH disruption");

        ArgumentCaptor<FlightStatusHistory> captor = ArgumentCaptor.forClass(FlightStatusHistory.class);
        verify(flightStatusHistoryRepository).saveAndFlush(captor.capture());

        FlightStatusHistory history = captor.getValue();
        assertThat(history.getPreviousStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(history.getNewStatus()).isEqualTo(FlightStatus.DELAYED);
        assertThat(history.getSource()).isEqualTo(TransitionSource.DISRUPTION_AUTO);
        assertThat(history.getReason()).isEqualTo("Auto-delayed due to HIGH disruption");
        assertThat(history.getChangedAt()).isNotNull();
    }

    @Test
    void applyThrowsAndPersistsNothingForIllegalTransition() {
        Flight flight = flightWithStatus(FlightStatus.ARRIVED);

        assertThatThrownBy(() -> flightStatusTransitionService.apply(flight, FlightStatus.BOARDING,
                TransitionSource.OPERATOR, null))
                .isInstanceOf(InvalidFlightStatusTransitionException.class);

        verify(flightRepository, never()).saveAndFlush(any());
        verify(flightStatusHistoryRepository, never()).saveAndFlush(any());
        verify(domainEventPublisher, never()).publish(any(), any(), any(), anyInt(), any());
    }

    @Test
    void seedInitialHistoryRecordsCreatedSourceWithNullPreviousStatusAndPublishesNoEvent() {
        Flight flight = flightWithStatus(FlightStatus.SCHEDULED);

        flightStatusTransitionService.seedInitialHistory(flight);

        ArgumentCaptor<FlightStatusHistory> captor = ArgumentCaptor.forClass(FlightStatusHistory.class);
        verify(flightStatusHistoryRepository).saveAndFlush(captor.capture());

        FlightStatusHistory history = captor.getValue();
        assertThat(history.getPreviousStatus()).isNull();
        assertThat(history.getNewStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(history.getSource()).isEqualTo(TransitionSource.CREATED);

        verify(domainEventPublisher, never()).publish(any(), any(), any(), anyInt(), any());
        verify(flightRepository, never()).saveAndFlush(any());
    }
}
