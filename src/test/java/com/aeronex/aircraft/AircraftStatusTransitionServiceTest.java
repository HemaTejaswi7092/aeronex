package com.aeronex.aircraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aeronex.aircraft.exception.InvalidAircraftStatusTransitionException;
import com.aeronex.eventing.DomainEventPublisher;
import com.aeronex.eventing.kafka.KafkaTopics;

@ExtendWith(MockitoExtension.class)
class AircraftStatusTransitionServiceTest {

    @Mock
    private AircraftRepository aircraftRepository;

    @Mock
    private AircraftStatusHistoryRepository aircraftStatusHistoryRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private AircraftStatusTransitionService aircraftStatusTransitionService;

    private Aircraft aircraftWithStatus(AircraftStatus status) {
        return new Aircraft("N12345", "Boeing", "737-800", 189, status);
    }

    @BeforeEach
    void setUp() {
        aircraftStatusTransitionService = new AircraftStatusTransitionService(aircraftRepository,
                aircraftStatusHistoryRepository, domainEventPublisher);
    }

    @Test
    void applySavesAircraftRecordsHistoryAndPublishesEvent() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.ACTIVE);
        when(aircraftRepository.saveAndFlush(aircraft)).thenReturn(aircraft);

        Aircraft result = aircraftStatusTransitionService.apply(aircraft, AircraftStatus.MAINTENANCE,
                AircraftTransitionSource.OPERATOR, "Scheduled check");

        assertThat(result.getStatus()).isEqualTo(AircraftStatus.MAINTENANCE);
        verify(aircraftRepository).saveAndFlush(aircraft);
        verify(aircraftStatusHistoryRepository).saveAndFlush(any(AircraftStatusHistory.class));
        verify(domainEventPublisher).publish(eq(KafkaTopics.AIRCRAFT_STATUS_CHANGED), any(),
                eq("AircraftStatusChanged"), eq(1), any());
    }

    @Test
    void applyRecordsCorrectPreviousAndNewStatusInHistory() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.ACTIVE);
        when(aircraftRepository.saveAndFlush(aircraft)).thenReturn(aircraft);

        aircraftStatusTransitionService.apply(aircraft, AircraftStatus.MAINTENANCE,
                AircraftTransitionSource.DISRUPTION_AUTO, "Auto-maintenance due to MECHANICAL disruption");

        ArgumentCaptor<AircraftStatusHistory> captor = ArgumentCaptor.forClass(AircraftStatusHistory.class);
        verify(aircraftStatusHistoryRepository).saveAndFlush(captor.capture());

        AircraftStatusHistory history = captor.getValue();
        assertThat(history.getPreviousStatus()).isEqualTo(AircraftStatus.ACTIVE);
        assertThat(history.getNewStatus()).isEqualTo(AircraftStatus.MAINTENANCE);
        assertThat(history.getSource()).isEqualTo(AircraftTransitionSource.DISRUPTION_AUTO);
        assertThat(history.getReason()).isEqualTo("Auto-maintenance due to MECHANICAL disruption");
        assertThat(history.getChangedAt()).isNotNull();
    }

    @Test
    void applyThrowsAndPersistsNothingForIllegalTransition() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.MAINTENANCE);

        assertThatThrownBy(() -> aircraftStatusTransitionService.apply(aircraft, AircraftStatus.ACTIVE,
                AircraftTransitionSource.OPERATOR, null))
                .isInstanceOf(InvalidAircraftStatusTransitionException.class);

        verify(aircraftRepository, never()).saveAndFlush(any());
        verify(aircraftStatusHistoryRepository, never()).saveAndFlush(any());
        verify(domainEventPublisher, never()).publish(any(), any(), any(), anyInt(), any());
    }

    @Test
    void seedInitialHistoryRecordsCreatedSourceWithNullPreviousStatusAndPublishesNoEvent() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.ACTIVE);

        aircraftStatusTransitionService.seedInitialHistory(aircraft);

        ArgumentCaptor<AircraftStatusHistory> captor = ArgumentCaptor.forClass(AircraftStatusHistory.class);
        verify(aircraftStatusHistoryRepository).saveAndFlush(captor.capture());

        AircraftStatusHistory history = captor.getValue();
        assertThat(history.getPreviousStatus()).isNull();
        assertThat(history.getNewStatus()).isEqualTo(AircraftStatus.ACTIVE);
        assertThat(history.getSource()).isEqualTo(AircraftTransitionSource.CREATED);

        verify(domainEventPublisher, never()).publish(any(), any(), any(), anyInt(), any());
        verify(aircraftRepository, never()).saveAndFlush(any());
    }
}
