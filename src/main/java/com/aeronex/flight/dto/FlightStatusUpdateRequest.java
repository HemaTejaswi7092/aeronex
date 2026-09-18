package com.aeronex.flight.dto;

import com.aeronex.flight.FlightStatus;

import jakarta.validation.constraints.NotNull;

public record FlightStatusUpdateRequest(

        @NotNull(message = "status is required")
        FlightStatus status,

        String reason
) {
}
