package com.aeronex.disruption;

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

import com.aeronex.disruption.dto.DisruptionCreateRequest;
import com.aeronex.disruption.dto.DisruptionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/disruptions")
public class DisruptionController {

    private final DisruptionService disruptionService;

    public DisruptionController(DisruptionService disruptionService) {
        this.disruptionService = disruptionService;
    }

    @PostMapping
    public ResponseEntity<DisruptionResponse> create(@Valid @RequestBody DisruptionCreateRequest request) {
        DisruptionResponse created = disruptionService.create(request);
        return ResponseEntity.created(URI.create("/api/disruptions/" + created.id())).body(created);
    }

    @GetMapping
    public List<DisruptionResponse> getAll() {
        return disruptionService.findAll();
    }

    @GetMapping("/active")
    public List<DisruptionResponse> getActive() {
        return disruptionService.findActive();
    }

    @GetMapping("/{id}")
    public DisruptionResponse getById(@PathVariable UUID id) {
        return disruptionService.findById(id);
    }

    @GetMapping("/flight/{flightId}")
    public List<DisruptionResponse> getByFlight(@PathVariable UUID flightId) {
        return disruptionService.findByFlight(flightId);
    }

    @PatchMapping("/{id}/resolve")
    public DisruptionResponse resolve(@PathVariable UUID id) {
        return disruptionService.resolve(id);
    }
}
