package com.aeronex.aircraft;

import com.aeronex.aircraft.dto.AircraftResponse;

final class AircraftMapper {

    private AircraftMapper() {
    }

    static AircraftResponse toResponse(Aircraft aircraft) {
        return new AircraftResponse(
                aircraft.getId(),
                aircraft.getRegistrationNumber(),
                aircraft.getManufacturer(),
                aircraft.getModel(),
                aircraft.getSeatCapacity(),
                aircraft.getStatus(),
                aircraft.getCreatedAt(),
                aircraft.getUpdatedAt()
        );
    }
}
