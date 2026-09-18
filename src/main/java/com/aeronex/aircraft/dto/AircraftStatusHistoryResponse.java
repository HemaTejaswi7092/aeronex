package com.aeronex.aircraft.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.aircraft.AircraftStatus;
import com.aeronex.aircraft.AircraftTransitionSource;

public record AircraftStatusHistoryResponse(
        UUID id,
        AircraftStatus previousStatus,
        AircraftStatus newStatus,
        AircraftTransitionSource source,
        String reason,
        OffsetDateTime changedAt
) {
}
