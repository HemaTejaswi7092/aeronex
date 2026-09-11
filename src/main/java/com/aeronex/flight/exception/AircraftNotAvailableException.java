package com.aeronex.flight.exception;

public class AircraftNotAvailableException extends RuntimeException {

    public AircraftNotAvailableException(String message) {
        super(message);
    }
}
