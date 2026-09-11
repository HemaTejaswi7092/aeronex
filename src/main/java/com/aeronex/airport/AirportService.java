package com.aeronex.airport;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.airport.dto.AirportCreateRequest;
import com.aeronex.airport.dto.AirportResponse;
import com.aeronex.airport.exception.AirportNotFoundException;
import com.aeronex.airport.exception.DuplicateAirportCodeException;

@Service
@Transactional(readOnly = true)
public class AirportService {

    private final AirportRepository airportRepository;

    public AirportService(AirportRepository airportRepository) {
        this.airportRepository = airportRepository;
    }

    @Transactional
    public AirportResponse create(AirportCreateRequest request) {
        if (airportRepository.existsByIataCode(request.iataCode())) {
            throw new DuplicateAirportCodeException(
                    "Airport with IATA code " + request.iataCode() + " already exists");
        }
        if (request.icaoCode() != null && airportRepository.existsByIcaoCode(request.icaoCode())) {
            throw new DuplicateAirportCodeException(
                    "Airport with ICAO code " + request.icaoCode() + " already exists");
        }

        Airport airport = new Airport(
                request.iataCode(),
                request.icaoCode(),
                request.name(),
                request.city(),
                request.country(),
                request.timezone(),
                request.latitude(),
                request.longitude()
        );

        try {
            Airport saved = airportRepository.save(airport);
            return AirportMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateAirportCodeException(
                    "Airport with IATA code " + request.iataCode() + " or ICAO code " + request.icaoCode()
                            + " already exists", e);
        }
    }

    public List<AirportResponse> findAll() {
        return airportRepository.findAll().stream()
                .map(AirportMapper::toResponse)
                .toList();
    }

    public AirportResponse findById(UUID id) {
        return airportRepository.findById(id)
                .map(AirportMapper::toResponse)
                .orElseThrow(() -> AirportNotFoundException.forId(id));
    }

    public AirportResponse findByIataCode(String iataCode) {
        String normalized = iataCode.trim().toUpperCase();
        return airportRepository.findByIataCode(normalized)
                .map(AirportMapper::toResponse)
                .orElseThrow(() -> AirportNotFoundException.forIataCode(normalized));
    }
}
