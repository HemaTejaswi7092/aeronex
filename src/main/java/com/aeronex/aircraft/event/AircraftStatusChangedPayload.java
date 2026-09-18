package com.aeronex.aircraft.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.aircraft.AircraftStatus;
import com.aeronex.aircraft.AircraftTransitionSource;

public record AircraftStatusChangedPayload(
        UUID aircraftId,
        String registrationNumber,
        AircraftStatus previousStatus,
        AircraftStatus newStatus,
        AircraftTransitionSource source,
        String reason,
        OffsetDateTime changedAt
) {
}
