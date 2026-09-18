package com.aeronex.aircraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.aeronex.aircraft.dto.AircraftCreateRequest;
import com.aeronex.aircraft.dto.AircraftResponse;
import com.aeronex.aircraft.dto.AircraftStatusHistoryResponse;
import com.aeronex.aircraft.dto.AircraftStatusUpdateRequest;
import com.aeronex.aircraft.exception.AircraftNotFoundException;
import com.aeronex.aircraft.exception.DuplicateRegistrationNumberException;
import com.aeronex.aircraft.exception.InvalidAircraftStatusTransitionException;

@ExtendWith(MockitoExtension.class)
class AircraftServiceTest {

    @Mock
    private AircraftRepository aircraftRepository;

    @Mock
    private AircraftStatusHistoryRepository aircraftStatusHistoryRepository;

    @Mock
    private AircraftStatusTransitionService aircraftStatusTransitionService;

    private AircraftService aircraftService;

    private AircraftCreateRequest sampleRequest(AircraftStatus status) {
        return new AircraftCreateRequest("n12345", "Boeing", "737-800", 189, status);
    }

    private Aircraft aircraftWithId(UUID id, AircraftStatus status) {
        Aircraft aircraft = new Aircraft("N12345", "Boeing", "737-800", 189, status);
        ReflectionTestUtils.setField(aircraft, "id", id);
        return aircraft;
    }

    @BeforeEach
    void setUp() {
        aircraftService = new AircraftService(aircraftRepository, aircraftStatusHistoryRepository,
                aircraftStatusTransitionService);
    }

    @Test
    void createNormalizesRegistrationNumberToUppercase() {
        when(aircraftRepository.existsByRegistrationNumber("N12345")).thenReturn(false);
        when(aircraftRepository.saveAndFlush(any(Aircraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AircraftResponse response = aircraftService.create(sampleRequest(AircraftStatus.ACTIVE));

        assertThat(response.registrationNumber()).isEqualTo("N12345");
        verify(aircraftStatusTransitionService).seedInitialHistory(any(Aircraft.class));
    }

    @Test
    void createDefaultsStatusToActiveWhenNotProvided() {
        when(aircraftRepository.existsByRegistrationNumber("N12345")).thenReturn(false);
        when(aircraftRepository.saveAndFlush(any(Aircraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AircraftResponse response = aircraftService.create(sampleRequest(null));

        assertThat(response.status()).isEqualTo(AircraftStatus.ACTIVE);
    }

    @Test
    void createPreservesExplicitStatus() {
        when(aircraftRepository.existsByRegistrationNumber("N12345")).thenReturn(false);
        when(aircraftRepository.saveAndFlush(any(Aircraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AircraftResponse response = aircraftService.create(sampleRequest(AircraftStatus.MAINTENANCE));

        assertThat(response.status()).isEqualTo(AircraftStatus.MAINTENANCE);
    }

    @Test
    void createThrowsWhenRegistrationNumberAlreadyExists() {
        when(aircraftRepository.existsByRegistrationNumber("N12345")).thenReturn(true);

        assertThatThrownBy(() -> aircraftService.create(sampleRequest(AircraftStatus.ACTIVE)))
                .isInstanceOf(DuplicateRegistrationNumberException.class);
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(aircraftRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aircraftService.findById(id))
                .isInstanceOf(AircraftNotFoundException.class);
    }

    @Test
    void findByRegistrationNumberNormalizesInputBeforeLookup() {
        when(aircraftRepository.findByRegistrationNumber("N12345")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aircraftService.findByRegistrationNumber(" n12345 "))
                .isInstanceOf(AircraftNotFoundException.class);
    }

    @Test
    void updateStatusDelegatesToTransitionServiceAndReturnsResult() {
        UUID id = UUID.randomUUID();
        Aircraft aircraft = aircraftWithId(id, AircraftStatus.ACTIVE);
        Aircraft transitioned = aircraftWithId(id, AircraftStatus.MAINTENANCE);

        when(aircraftRepository.findById(id)).thenReturn(Optional.of(aircraft));
        when(aircraftStatusTransitionService.apply(aircraft, AircraftStatus.MAINTENANCE,
                AircraftTransitionSource.OPERATOR, "Scheduled check")).thenReturn(transitioned);

        AircraftResponse response = aircraftService.updateStatus(id,
                new AircraftStatusUpdateRequest(AircraftStatus.MAINTENANCE, "Scheduled check"));

        assertThat(response.status()).isEqualTo(AircraftStatus.MAINTENANCE);
        verify(aircraftStatusTransitionService).apply(aircraft, AircraftStatus.MAINTENANCE,
                AircraftTransitionSource.OPERATOR, "Scheduled check");
    }

    @Test
    void updateStatusThrowsWhenAircraftNotFound() {
        UUID id = UUID.randomUUID();
        when(aircraftRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aircraftService.updateStatus(id,
                new AircraftStatusUpdateRequest(AircraftStatus.MAINTENANCE, null)))
                .isInstanceOf(AircraftNotFoundException.class);

        verifyNoInteractions(aircraftStatusTransitionService);
    }

    @Test
    void updateStatusPropagatesInvalidTransitionFromTransitionService() {
        UUID id = UUID.randomUUID();
        Aircraft aircraft = aircraftWithId(id, AircraftStatus.OUT_OF_SERVICE);

        when(aircraftRepository.findById(id)).thenReturn(Optional.of(aircraft));
        when(aircraftStatusTransitionService.apply(eq(aircraft), eq(AircraftStatus.MAINTENANCE),
                eq(AircraftTransitionSource.OPERATOR), any()))
                .thenThrow(new InvalidAircraftStatusTransitionException(
                        "Cannot transition aircraft N12345 from OUT_OF_SERVICE to MAINTENANCE"));

        assertThatThrownBy(() -> aircraftService.updateStatus(id,
                new AircraftStatusUpdateRequest(AircraftStatus.MAINTENANCE, null)))
                .isInstanceOf(InvalidAircraftStatusTransitionException.class);
    }

    @Test
    void getStatusHistoryReturnsChronologicalList() {
        UUID id = UUID.randomUUID();
        Aircraft aircraft = aircraftWithId(id, AircraftStatus.MAINTENANCE);
        AircraftStatusHistory first = new AircraftStatusHistory(aircraft, null, AircraftStatus.ACTIVE,
                AircraftTransitionSource.CREATED, null, java.time.OffsetDateTime.parse("2026-06-01T08:00:00Z"));
        AircraftStatusHistory second = new AircraftStatusHistory(aircraft, AircraftStatus.ACTIVE,
                AircraftStatus.MAINTENANCE, AircraftTransitionSource.OPERATOR, "Scheduled check",
                java.time.OffsetDateTime.parse("2026-06-01T09:00:00Z"));

        when(aircraftRepository.existsById(id)).thenReturn(true);
        when(aircraftStatusHistoryRepository.findByAircraftIdOrderByChangedAtAsc(id))
                .thenReturn(List.of(first, second));

        List<AircraftStatusHistoryResponse> history = aircraftService.getStatusHistory(id);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).previousStatus()).isNull();
        assertThat(history.get(0).newStatus()).isEqualTo(AircraftStatus.ACTIVE);
        assertThat(history.get(0).source()).isEqualTo(AircraftTransitionSource.CREATED);
        assertThat(history.get(1).previousStatus()).isEqualTo(AircraftStatus.ACTIVE);
        assertThat(history.get(1).newStatus()).isEqualTo(AircraftStatus.MAINTENANCE);
        assertThat(history.get(1).source()).isEqualTo(AircraftTransitionSource.OPERATOR);
        assertThat(history.get(1).reason()).isEqualTo("Scheduled check");
    }

    @Test
    void getStatusHistoryThrowsWhenAircraftNotFound() {
        UUID id = UUID.randomUUID();
        when(aircraftRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> aircraftService.getStatusHistory(id))
                .isInstanceOf(AircraftNotFoundException.class);
    }
}
