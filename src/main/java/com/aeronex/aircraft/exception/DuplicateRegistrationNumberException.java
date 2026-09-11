package com.aeronex.aircraft.exception;

public class DuplicateRegistrationNumberException extends RuntimeException {

    public DuplicateRegistrationNumberException(String message) {
        super(message);
    }

    public DuplicateRegistrationNumberException(String message, Throwable cause) {
        super(message, cause);
    }
}
