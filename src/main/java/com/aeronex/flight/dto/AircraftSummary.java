package com.aeronex.flight.dto;

import java.util.UUID;

import com.aeronex.aircraft.AircraftStatus;

public record AircraftSummary(
        UUID id,
        String registrationNumber,
        String manufacturer,
        String model,
        AircraftStatus status
) {
}
