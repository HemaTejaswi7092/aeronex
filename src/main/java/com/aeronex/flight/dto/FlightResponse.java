package com.aeronex.flight.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.flight.FlightStatus;

public record FlightResponse(
        UUID id,
        String flightNumber,
        AirportSummary originAirport,
        AirportSummary destinationAirport,
        AircraftSummary aircraft,
        OffsetDateTime scheduledDepartureTime,
        OffsetDateTime scheduledArrivalTime,
        OffsetDateTime actualDepartureTime,
        OffsetDateTime actualArrivalTime,
        FlightStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
