package com.aeronex.airport.exception;

public class DuplicateAirportCodeException extends RuntimeException {

    public DuplicateAirportCodeException(String message) {
        super(message);
    }

    public DuplicateAirportCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
