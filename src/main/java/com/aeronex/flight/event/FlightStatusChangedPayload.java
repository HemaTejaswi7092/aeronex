package com.aeronex.flight.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.flight.FlightStatus;
import com.aeronex.flight.TransitionSource;

public record FlightStatusChangedPayload(
        UUID flightId,
        String flightNumber,
        FlightStatus previousStatus,
        FlightStatus newStatus,
        TransitionSource source,
        String reason,
        OffsetDateTime changedAt
) {
}
