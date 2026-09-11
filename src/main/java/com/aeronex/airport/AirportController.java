package com.aeronex.airport;

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

import com.aeronex.airport.dto.AirportCreateRequest;
import com.aeronex.airport.dto.AirportResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/airports")
public class AirportController {

    private final AirportService airportService;

    public AirportController(AirportService airportService) {
        this.airportService = airportService;
    }

    @PostMapping
    public ResponseEntity<AirportResponse> create(@Valid @RequestBody AirportCreateRequest request) {
        AirportResponse created = airportService.create(request);
        return ResponseEntity.created(URI.create("/api/airports/" + created.id())).body(created);
    }

    @GetMapping
    public List<AirportResponse> getAll() {
        return airportService.findAll();
    }

    @GetMapping("/{id}")
    public AirportResponse getById(@PathVariable UUID id) {
        return airportService.findById(id);
    }

    @GetMapping("/iata/{iataCode}")
    public AirportResponse getByIataCode(@PathVariable String iataCode) {
        return airportService.findByIataCode(iataCode);
    }
}
