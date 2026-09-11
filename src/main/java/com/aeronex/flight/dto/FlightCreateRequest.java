package com.aeronex.flight.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.flight.FlightStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FlightCreateRequest(

        @NotBlank(message = "flightNumber is required")
        @Size(max = 10, message = "flightNumber must be at most 10 characters")
        String flightNumber,

        @NotNull(message = "originAirportId is required")
        UUID originAirportId,

        @NotNull(message = "destinationAirportId is required")
        UUID destinationAirportId,

        UUID aircraftId,

        @NotNull(message = "scheduledDepartureTime is required")
        OffsetDateTime scheduledDepartureTime,

        @NotNull(message = "scheduledArrivalTime is required")
        OffsetDateTime scheduledArrivalTime,

        OffsetDateTime actualDepartureTime,

        OffsetDateTime actualArrivalTime,

        FlightStatus status
) {

    public FlightCreateRequest {
        if (flightNumber != null) {
            flightNumber = flightNumber.trim().toUpperCase();
        }
    }
}
