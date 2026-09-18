package com.aeronex.flight;

import com.aeronex.aircraft.Aircraft;
import com.aeronex.airport.Airport;
import com.aeronex.flight.dto.AircraftSummary;
import com.aeronex.flight.dto.AirportSummary;
import com.aeronex.flight.dto.FlightResponse;
import com.aeronex.flight.dto.FlightStatusHistoryResponse;

final class FlightMapper {

    private FlightMapper() {
    }

    static FlightResponse toResponse(Flight flight) {
        return new FlightResponse(
                flight.getId(),
                flight.getFlightNumber(),
                toAirportSummary(flight.getOriginAirport()),
                toAirportSummary(flight.getDestinationAirport()),
                flight.getAircraft() != null ? toAircraftSummary(flight.getAircraft()) : null,
                flight.getScheduledDepartureTime(),
                flight.getScheduledArrivalTime(),
                flight.getActualDepartureTime(),
                flight.getActualArrivalTime(),
                flight.getStatus(),
                flight.getCreatedAt(),
                flight.getUpdatedAt()
        );
    }

    static FlightStatusHistoryResponse toHistoryResponse(FlightStatusHistory history) {
        return new FlightStatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getSource(),
                history.getReason(),
                history.getChangedAt()
        );
    }

    private static AirportSummary toAirportSummary(Airport airport) {
        return new AirportSummary(
                airport.getId(),
                airport.getIataCode(),
                airport.getName(),
                airport.getCity(),
                airport.getCountry()
        );
    }

    private static AircraftSummary toAircraftSummary(Aircraft aircraft) {
        return new AircraftSummary(
                aircraft.getId(),
                aircraft.getRegistrationNumber(),
                aircraft.getManufacturer(),
                aircraft.getModel(),
                aircraft.getStatus()
        );
    }
}
