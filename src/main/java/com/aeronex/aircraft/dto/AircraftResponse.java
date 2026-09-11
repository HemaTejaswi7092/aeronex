package com.aeronex.aircraft.dto;

import java.time.Instant;
import java.util.UUID;

import com.aeronex.aircraft.AircraftStatus;

public record AircraftResponse(
        UUID id,
        String registrationNumber,
        String manufacturer,
        String model,
        Integer seatCapacity,
        AircraftStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
