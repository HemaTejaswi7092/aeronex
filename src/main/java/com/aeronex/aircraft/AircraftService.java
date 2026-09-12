package com.aeronex.aircraft;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.aircraft.dto.AircraftCreateRequest;
import com.aeronex.aircraft.dto.AircraftResponse;
import com.aeronex.aircraft.exception.AircraftNotFoundException;
import com.aeronex.aircraft.exception.DuplicateRegistrationNumberException;

@Service
@Transactional(readOnly = true)
public class AircraftService {

    private final AircraftRepository aircraftRepository;

    public AircraftService(AircraftRepository aircraftRepository) {
        this.aircraftRepository = aircraftRepository;
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

        try {
            Aircraft saved = aircraftRepository.saveAndFlush(aircraft);
            return AircraftMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateRegistrationNumberException(
                    "Aircraft with registration number " + request.registrationNumber() + " already exists", e);
        }
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
