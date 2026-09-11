package com.aeronex.flight.exception;

import java.util.UUID;

public class FlightNotFoundException extends RuntimeException {

    public FlightNotFoundException(String message) {
        super(message);
    }

    public static FlightNotFoundException forId(UUID id) {
        return new FlightNotFoundException("Flight not found with id " + id);
    }
}
