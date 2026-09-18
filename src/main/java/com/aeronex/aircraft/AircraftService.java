package com.aeronex.aircraft;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.aircraft.dto.AircraftCreateRequest;
import com.aeronex.aircraft.dto.AircraftResponse;
import com.aeronex.aircraft.dto.AircraftStatusHistoryResponse;
import com.aeronex.aircraft.dto.AircraftStatusUpdateRequest;
import com.aeronex.aircraft.exception.AircraftNotFoundException;
import com.aeronex.aircraft.exception.DuplicateRegistrationNumberException;

@Service
@Transactional(readOnly = true)
public class AircraftService {

    private final AircraftRepository aircraftRepository;
    private final AircraftStatusHistoryRepository aircraftStatusHistoryRepository;
    private final AircraftStatusTransitionService aircraftStatusTransitionService;

    public AircraftService(AircraftRepository aircraftRepository,
                            AircraftStatusHistoryRepository aircraftStatusHistoryRepository,
                            AircraftStatusTransitionService aircraftStatusTransitionService) {
        this.aircraftRepository = aircraftRepository;
        this.aircraftStatusHistoryRepository = aircraftStatusHistoryRepository;
        this.aircraftStatusTransitionService = aircraftStatusTransitionService;
    }

    @Transactional
    public AircraftResponse create(AircraftCreateRequest request) {
        if (aircraftRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw new DuplicateRegistrationNumberException(
                    "Aircraft with registration number " + request.registrationNumber() + " already exists");
        }

        AircraftStatus status = request.status() != null ? request.status() : AircraftStatus.ACTIVE;

        Aircraft aircraft = new Aircraft(
                request.registrationNumber(),
                request.manufacturer(),
                request.model(),
                request.seatCapacity(),
                status
        );

        Aircraft saved;
        try {
            saved = aircraftRepository.saveAndFlush(aircraft);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateRegistrationNumberException(
                    "Aircraft with registration number " + request.registrationNumber() + " already exists", e);
        }

        aircraftStatusTransitionService.seedInitialHistory(saved);
        return AircraftMapper.toResponse(saved);
    }

    @Transactional
    public AircraftResponse updateStatus(UUID id, AircraftStatusUpdateRequest request) {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> AircraftNotFoundException.forId(id));

        Aircraft saved = aircraftStatusTransitionService.apply(aircraft, request.status(),
                AircraftTransitionSource.OPERATOR, request.reason());

        return AircraftMapper.toResponse(saved);
    }

    public List<AircraftStatusHistoryResponse> getStatusHistory(UUID id) {
        if (!aircraftRepository.existsById(id)) {
            throw AircraftNotFoundException.forId(id);
        }
        return aircraftStatusHistoryRepository.findByAircraftIdOrderByChangedAtAsc(id).stream()
                .map(AircraftMapper::toHistoryResponse)
                .toList();
    }

    public List<AircraftResponse> findAll() {
        return aircraftRepository.findAll().stream()
                .map(AircraftMapper::toResponse)
                .toList();
    }

    public AircraftResponse findById(UUID id) {
        return aircraftRepository.findById(id)
                .map(AircraftMapper::toResponse)
                .orElseThrow(() -> AircraftNotFoundException.forId(id));
    }

    public AircraftResponse findByRegistrationNumber(String registrationNumber) {
        String normalized = registrationNumber.trim().toUpperCase();
        return aircraftRepository.findByRegistrationNumber(normalized)
                .map(AircraftMapper::toResponse)
                .orElseThrow(() -> AircraftNotFoundException.forRegistrationNumber(normalized));
    }
}
