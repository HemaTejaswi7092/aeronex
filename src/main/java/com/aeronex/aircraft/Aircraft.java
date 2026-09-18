package com.aeronex.aircraft;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.aeronex.aircraft.exception.InvalidAircraftStatusTransitionException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "aircraft")
public class Aircraft {

    // The single source of truth for legal aircraft lifecycle transitions. Both
    // operator-requested updates (AircraftStatusTransitionService, called from
    // AircraftService) and automatic disruption-driven transitions (the same
    // service, called from DisruptionService) go through transitionTo() below
    // — there is no second copy of this table anywhere in the codebase. Note
    // MAINTENANCE has only one exit, to OUT_OF_SERVICE: there is no direct
    // MAINTENANCE -> ACTIVE path, by explicit design.
    private static final Map<AircraftStatus, Set<AircraftStatus>> ALLOWED_TRANSITIONS = Map.of(
            AircraftStatus.ACTIVE, Set.of(AircraftStatus.MAINTENANCE, AircraftStatus.OUT_OF_SERVICE),
            AircraftStatus.MAINTENANCE, Set.of(AircraftStatus.OUT_OF_SERVICE),
            AircraftStatus.OUT_OF_SERVICE, Set.of(AircraftStatus.ACTIVE)
    );

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "registration_number", nullable = false, unique = true, length = 20)
    private String registrationNumber;

    @Column(nullable = false)
    private String manufacturer;

    @Column(nullable = false)
    private String model;

    @Column(name = "seat_capacity", nullable = false)
    private Integer seatCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AircraftStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Aircraft() {
    }

    public Aircraft(String registrationNumber, String manufacturer, String model,
                     Integer seatCapacity, AircraftStatus status) {
        this.registrationNumber = registrationNumber;
        this.manufacturer = manufacturer;
        this.model = model;
        this.seatCapacity = seatCapacity;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public Integer getSeatCapacity() {
        return seatCapacity;
    }

    public AircraftStatus getStatus() {
        return status;
    }

    public boolean canTransitionTo(AircraftStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(target);
    }

    public void transitionTo(AircraftStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidAircraftStatusTransitionException(
                    "Cannot transition aircraft " + registrationNumber + " from " + status + " to " + target);
        }
        this.status = target;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
