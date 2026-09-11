package com.aeronex.airport;

import com.aeronex.airport.dto.AirportResponse;

final class AirportMapper {

    private AirportMapper() {
    }

    static AirportResponse toResponse(Airport airport) {
        return new AirportResponse(
                airport.getId(),
                airport.getIataCode(),
                airport.getIcaoCode(),
                airport.getName(),
                airport.getCity(),
                airport.getCountry(),
                airport.getTimezone(),
                airport.getLatitude(),
                airport.getLongitude(),
                airport.getCreatedAt(),
                airport.getUpdatedAt()
        );
    }
}
