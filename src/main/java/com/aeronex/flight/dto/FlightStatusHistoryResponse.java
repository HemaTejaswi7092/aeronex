package com.aeronex.flight.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.flight.FlightStatus;
import com.aeronex.flight.TransitionSource;

public record FlightStatusHistoryResponse(
        UUID id,
        FlightStatus previousStatus,
        FlightStatus newStatus,
        TransitionSource source,
        String reason,
        OffsetDateTime changedAt
) {
}
