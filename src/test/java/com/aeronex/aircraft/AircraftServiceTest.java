package com.aeronex.aircraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aeronex.aircraft.dto.AircraftCreateRequest;
import com.aeronex.aircraft.dto.AircraftResponse;
import com.aeronex.aircraft.exception.AircraftNotFoundException;
import com.aeronex.aircraft.exception.DuplicateRegistrationNumberException;

@ExtendWith(MockitoExtension.class)
class AircraftServiceTest {

    @Mock
    private AircraftRepository aircraftRepository;

    private AircraftService aircraftService;

    private AircraftCreateRequest sampleRequest(AircraftStatus status) {
        return new AircraftCreateRequest("n12345", "Boeing", "737-800", 189, status);
    }

    @BeforeEach
    void setUp() {
        aircraftService = new AircraftService(aircraftRepository);
    }

    @Test
    void createNormalizesRegistrationNumberToUppercase() {
        when(aircraftRepository.existsByRegistrationNumber("N12345")).thenReturn(false);
        when(aircraftRepository.saveAndFlush(any(Aircraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AircraftResponse response = aircraftService.create(sampleRequest(AircraftStatus.ACTIVE));

        assertThat(response.registrationNumber()).isEqualTo("N12345");
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
}
