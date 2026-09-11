package com.aeronex.disruption.exception;

import java.util.UUID;

public class DisruptionNotFoundException extends RuntimeException {

    public DisruptionNotFoundException(String message) {
        super(message);
    }

    public static DisruptionNotFoundException forId(UUID id) {
        return new DisruptionNotFoundException("Disruption not found with id " + id);
    }
}
