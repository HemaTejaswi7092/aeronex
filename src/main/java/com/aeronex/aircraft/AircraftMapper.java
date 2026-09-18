package com.aeronex.aircraft;

import com.aeronex.aircraft.dto.AircraftResponse;
import com.aeronex.aircraft.dto.AircraftStatusHistoryResponse;

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

    static AircraftStatusHistoryResponse toHistoryResponse(AircraftStatusHistory history) {
        return new AircraftStatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getSource(),
                history.getReason(),
                history.getChangedAt()
        );
    }
}
