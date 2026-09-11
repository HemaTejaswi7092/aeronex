package com.aeronex.airport.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AirportCreateRequest(

        @NotBlank(message = "iataCode is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "iataCode must be exactly 3 letters")
        String iataCode,

        @Pattern(regexp = "^[A-Za-z]{4}$", message = "icaoCode must be exactly 4 letters")
        String icaoCode,

        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "city is required")
        String city,

        @NotBlank(message = "country is required")
        String country,

        @NotBlank(message = "timezone is required")
        String timezone,

        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90.0", message = "latitude must be >= -90")
        @DecimalMax(value = "90.0", message = "latitude must be <= 90")
        BigDecimal latitude,

        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180.0", message = "longitude must be >= -180")
        @DecimalMax(value = "180.0", message = "longitude must be <= 180")
        BigDecimal longitude
) {

    public AirportCreateRequest {
        if (iataCode != null) {
            iataCode = iataCode.trim().toUpperCase();
        }
        if (icaoCode != null) {
            icaoCode = icaoCode.trim().toUpperCase();
            if (icaoCode.isBlank()) {
                icaoCode = null;
            }
        }
    }
}
