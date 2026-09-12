package com.aeronex.flight;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.aircraft.Aircraft;
import com.aeronex.aircraft.AircraftRepository;
import com.aeronex.aircraft.AircraftStatus;
import com.aeronex.aircraft.exception.AircraftNotFoundException;
import com.aeronex.airport.Airport;
import com.aeronex.airport.AirportRepository;
import com.aeronex.airport.exception.AirportNotFoundException;
import com.aeronex.flight.dto.FlightCreateRequest;
import com.aeronex.flight.dto.FlightResponse;
import com.aeronex.flight.exception.AircraftNotAvailableException;
import com.aeronex.flight.exception.FlightNotFoundException;
import com.aeronex.flight.exception.InvalidFlightException;

@Service
@Transactional(readOnly = true)
public class FlightService {

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final AircraftRepository aircraftRepository;

    public FlightService(FlightRepository flightRepository, AirportRepository airportRepository,
                          AircraftRepository aircraftRepository) {
        this.flightRepository = flightRepository;
        this.airportRepository = airportRepository;
        this.aircraftRepository = aircraftRepository;
    }

    @Transactional
    public FlightResponse create(FlightCreateRequest request) {
        Airport origin = airportRepository.findById(request.originAirportId())
                .orElseThrow(() -> AirportNotFoundException.forId(request.originAirportId()));
        Airport destination = airportRepository.findById(request.destinationAirportId())
                .orElseThrow(() -> AirportNotFoundException.forId(request.destinationAirportId()));

        if (origin.getId().equals(destination.getId())) {
            throw new InvalidFlightException("Origin and destination airport cannot be the same");
        }

        if (!request.scheduledArrivalTime().isAfter(request.scheduledDepartureTime())) {
            throw new InvalidFlightException("scheduledArrivalTime must be after scheduledDepartureTime");
        }

        if (request.actualDepartureTime() != null && request.actualArrivalTime() != null
                && !request.actualArrivalTime().isAfter(request.actualDepartureTime())) {
            throw new InvalidFlightException("actualArrivalTime must be after actualDepartureTime");
        }

        Aircraft aircraft = null;
        if (request.aircraftId() != null) {
            aircraft = aircraftRepository.findById(request.aircraftId())
                    .orElseThrow(() -> AircraftNotFoundException.forId(request.aircraftId()));
            if (aircraft.getStatus() == AircraftStatus.OUT_OF_SERVICE) {
                throw new AircraftNotAvailableException(
                        "Aircraft with registration number " + aircraft.getRegistrationNumber()
                                + " is out of service and cannot be assigned to a flight");
            }
        }

        FlightStatus status = request.status() != null ? request.status() : FlightStatus.SCHEDULED;

        Flight flight = new Flight(
                request.flightNumber(),
                origin,
                destination,
                aircraft,
                request.scheduledDepartureTime(),
                request.scheduledArrivalTime(),
                request.actualDepartureTime(),
                request.actualArrivalTime(),
                status
        );

        Flight saved = flightRepository.saveAndFlush(flight);
        return FlightMapper.toResponse(saved);
    }

    public List<FlightResponse> findAll() {
        return flightRepository.findAllOrderByScheduledDepartureTime().stream()
                .map(FlightMapper::toResponse)
                .toList();
    }

    public FlightResponse findById(UUID id) {
        return flightRepository.findById(id)
                .map(FlightMapper::toResponse)
                .orElseThrow(() -> FlightNotFoundException.forId(id));
    }

    public List<FlightResponse> findByFlightNumber(String flightNumber) {
        String normalized = flightNumber.trim().toUpperCase();
        return flightRepository.findByFlightNumberOrderByScheduledDepartureTimeAsc(normalized).stream()
                .map(FlightMapper::toResponse)
                .toList();
    }

    public List<FlightResponse> findByAirport(String iataCode) {
        String normalized = iataCode.trim().toUpperCase();
        return flightRepository.findByOriginOrDestinationIataCode(normalized).stream()
                .map(FlightMapper::toResponse)
                .toList();
    }
}
