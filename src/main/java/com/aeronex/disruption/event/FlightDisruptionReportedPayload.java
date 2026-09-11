package com.aeronex.disruption.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.disruption.DisruptionSeverity;
import com.aeronex.disruption.DisruptionStatus;
import com.aeronex.disruption.DisruptionType;

public record FlightDisruptionReportedPayload(
        UUID disruptionId,
        UUID flightId,
        String flightNumber,
        String originIataCode,
        String destinationIataCode,
        OffsetDateTime scheduledDepartureTime,
        DisruptionType disruptionType,
        DisruptionSeverity severity,
        DisruptionStatus status,
        String description,
        Integer estimatedDelayMinutes,
        OffsetDateTime reportedAt
) {
}
