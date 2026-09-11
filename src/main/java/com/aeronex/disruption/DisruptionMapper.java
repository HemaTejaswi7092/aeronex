package com.aeronex.disruption;

import com.aeronex.disruption.dto.DisruptionResponse;
import com.aeronex.disruption.dto.FlightSummary;
import com.aeronex.flight.Flight;

final class DisruptionMapper {

    private DisruptionMapper() {
    }

    static DisruptionResponse toResponse(Disruption disruption) {
        return new DisruptionResponse(
                disruption.getId(),
                toFlightSummary(disruption.getFlight()),
                disruption.getType(),
                disruption.getSeverity(),
                disruption.getStatus(),
                disruption.getDescription(),
                disruption.getEstimatedDelayMinutes(),
                disruption.getReportedAt(),
                disruption.getResolvedAt(),
                disruption.getCreatedAt(),
                disruption.getUpdatedAt()
        );
    }

    private static FlightSummary toFlightSummary(Flight flight) {
        return new FlightSummary(
                flight.getId(),
                flight.getFlightNumber(),
                flight.getOriginAirport().getIataCode(),
                flight.getDestinationAirport().getIataCode(),
                flight.getScheduledDepartureTime(),
                flight.getStatus()
        );
    }
}
