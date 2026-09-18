package com.aeronex.flight.exception;

public class InvalidFlightStatusTransitionException extends RuntimeException {

    public InvalidFlightStatusTransitionException(String message) {
        super(message);
    }
}
