package com.aeronex.disruption.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.disruption.DisruptionSeverity;
import com.aeronex.disruption.DisruptionStatus;
import com.aeronex.disruption.DisruptionType;

public record DisruptionResponse(
        UUID id,
        FlightSummary flight,
        DisruptionType type,
        DisruptionSeverity severity,
        DisruptionStatus status,
        String description,
        Integer estimatedDelayMinutes,
        OffsetDateTime reportedAt,
        OffsetDateTime resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
