package com.aeronex.airport.exception;

import java.util.UUID;

public class AirportNotFoundException extends RuntimeException {

    public AirportNotFoundException(String message) {
        super(message);
    }

    public static AirportNotFoundException forId(UUID id) {
        return new AirportNotFoundException("Airport not found with id " + id);
    }

    public static AirportNotFoundException forIataCode(String iataCode) {
        return new AirportNotFoundException("Airport not found with IATA code " + iataCode);
    }
}
