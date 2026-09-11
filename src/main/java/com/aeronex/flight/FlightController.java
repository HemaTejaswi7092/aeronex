package com.aeronex.flight;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aeronex.flight.dto.FlightCreateRequest;
import com.aeronex.flight.dto.FlightResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    @PostMapping
    public ResponseEntity<FlightResponse> create(@Valid @RequestBody FlightCreateRequest request) {
        FlightResponse created = flightService.create(request);
        return ResponseEntity.created(URI.create("/api/flights/" + created.id())).body(created);
    }

    @GetMapping
    public List<FlightResponse> getAll() {
        return flightService.findAll();
    }

    @GetMapping("/{id}")
    public FlightResponse getById(@PathVariable UUID id) {
        return flightService.findById(id);
    }

    @GetMapping("/number/{flightNumber}")
    public List<FlightResponse> getByFlightNumber(@PathVariable String flightNumber) {
        return flightService.findByFlightNumber(flightNumber);
    }

    @GetMapping("/airport/{iataCode}")
    public List<FlightResponse> getByAirport(@PathVariable String iataCode) {
        return flightService.findByAirport(iataCode);
    }
}
