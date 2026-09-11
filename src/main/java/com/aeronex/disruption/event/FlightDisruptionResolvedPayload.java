package com.aeronex.disruption.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FlightDisruptionResolvedPayload(
        UUID disruptionId,
        UUID flightId,
        String flightNumber,
        OffsetDateTime reportedAt,
        OffsetDateTime resolvedAt,
        long resolutionDurationMinutes
) {
}
