package com.aeronex.disruption.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aeronex.disruption.DisruptionSeverity;
import com.aeronex.disruption.DisruptionStatus;
import com.aeronex.disruption.DisruptionType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DisruptionCreateRequest(

        @NotNull(message = "flightId is required")
        UUID flightId,

        @NotNull(message = "type is required")
        DisruptionType type,

        @NotNull(message = "severity is required")
        DisruptionSeverity severity,

        @NotBlank(message = "description is required")
        String description,

        @Min(value = 0, message = "estimatedDelayMinutes cannot be negative")
        Integer estimatedDelayMinutes,

        OffsetDateTime reportedAt,

        DisruptionStatus status,

        OffsetDateTime resolvedAt
) {
}
