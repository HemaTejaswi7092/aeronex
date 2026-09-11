package com.aeronex.aircraft.exception;

import java.util.UUID;

public class AircraftNotFoundException extends RuntimeException {

    public AircraftNotFoundException(String message) {
        super(message);
    }

    public static AircraftNotFoundException forId(UUID id) {
        return new AircraftNotFoundException("Aircraft not found with id " + id);
    }

    public static AircraftNotFoundException forRegistrationNumber(String registrationNumber) {
        return new AircraftNotFoundException("Aircraft not found with registration number " + registrationNumber);
    }
}
