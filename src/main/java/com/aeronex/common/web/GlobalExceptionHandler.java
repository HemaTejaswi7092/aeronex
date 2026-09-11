package com.aeronex.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aeronex.aircraft.exception.AircraftNotFoundException;
import com.aeronex.aircraft.exception.DuplicateRegistrationNumberException;
import com.aeronex.airport.exception.AirportNotFoundException;
import com.aeronex.airport.exception.DuplicateAirportCodeException;
import com.aeronex.flight.exception.AircraftNotAvailableException;
import com.aeronex.flight.exception.FlightNotFoundException;
import com.aeronex.flight.exception.InvalidFlightException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AirportNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAirportNotFound(AirportNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(DuplicateAirportCodeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAirportCode(DuplicateAirportCodeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(AircraftNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAircraftNotFound(AircraftNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(DuplicateRegistrationNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRegistrationNumber(DuplicateRegistrationNumberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(FlightNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFlightNotFound(FlightNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(InvalidFlightException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFlight(InvalidFlightException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(AircraftNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleAircraftNotAvailable(AircraftNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors));
    }
}
