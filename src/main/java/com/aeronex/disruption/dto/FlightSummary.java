package com.aeronex.disruption.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.flight.FlightStatus;

public record FlightSummary(
        UUID id,
        String flightNumber,
        String originIataCode,
        String destinationIataCode,
        OffsetDateTime scheduledDepartureTime,
        FlightStatus status
) {
}
