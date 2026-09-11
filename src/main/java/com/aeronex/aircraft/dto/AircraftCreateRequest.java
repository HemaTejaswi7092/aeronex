package com.aeronex.aircraft.dto;

import com.aeronex.aircraft.AircraftStatus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AircraftCreateRequest(

        @NotBlank(message = "registrationNumber is required")
        @Size(max = 20, message = "registrationNumber must be at most 20 characters")
        String registrationNumber,

        @NotBlank(message = "manufacturer is required")
        String manufacturer,

        @NotBlank(message = "model is required")
        String model,

        @NotNull(message = "seatCapacity is required")
        @Min(value = 1, message = "seatCapacity must be greater than 0")
        Integer seatCapacity,

        AircraftStatus status
) {

    public AircraftCreateRequest {
        if (registrationNumber != null) {
            registrationNumber = registrationNumber.trim().toUpperCase();
        }
    }
}
