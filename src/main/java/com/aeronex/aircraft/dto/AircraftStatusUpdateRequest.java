package com.aeronex.aircraft.dto;

import com.aeronex.aircraft.AircraftStatus;

import jakarta.validation.constraints.NotNull;

public record AircraftStatusUpdateRequest(

        @NotNull(message = "status is required")
        AircraftStatus status,

        String reason
) {
}
