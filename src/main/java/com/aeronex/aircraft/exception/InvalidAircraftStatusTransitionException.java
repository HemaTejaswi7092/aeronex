package com.aeronex.aircraft.exception;

public class InvalidAircraftStatusTransitionException extends RuntimeException {

    public InvalidAircraftStatusTransitionException(String message) {
        super(message);
    }
}
