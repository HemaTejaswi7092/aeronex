package com.aeronex.aircraft;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aeronex.aircraft.dto.AircraftCreateRequest;
import com.aeronex.aircraft.dto.AircraftResponse;
import com.aeronex.aircraft.dto.AircraftStatusHistoryResponse;
import com.aeronex.aircraft.dto.AircraftStatusUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/aircraft")
public class AircraftController {

    private final AircraftService aircraftService;

    public AircraftController(AircraftService aircraftService) {
        this.aircraftService = aircraftService;
    }

    @PostMapping
    public ResponseEntity<AircraftResponse> create(@Valid @RequestBody AircraftCreateRequest request) {
        AircraftResponse created = aircraftService.create(request);
        return ResponseEntity.created(URI.create("/api/aircraft/" + created.id())).body(created);
    }

    @GetMapping
    public List<AircraftResponse> getAll() {
        return aircraftService.findAll();
    }

    @GetMapping("/{id}")
    public AircraftResponse getById(@PathVariable UUID id) {
        return aircraftService.findById(id);
    }

    @GetMapping("/registration/{registrationNumber}")
    public AircraftResponse getByRegistrationNumber(@PathVariable String registrationNumber) {
        return aircraftService.findByRegistrationNumber(registrationNumber);
    }

    @PatchMapping("/{id}/status")
    public AircraftResponse updateStatus(@PathVariable UUID id,
                                          @Valid @RequestBody AircraftStatusUpdateRequest request) {
        return aircraftService.updateStatus(id, request);
    }

    @GetMapping("/{id}/status-history")
    public List<AircraftStatusHistoryResponse> getStatusHistory(@PathVariable UUID id) {
        return aircraftService.getStatusHistory(id);
    }
}
