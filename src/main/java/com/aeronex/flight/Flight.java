package com.aeronex.flight;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.aeronex.aircraft.Aircraft;
import com.aeronex.airport.Airport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "flights")
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flight_number", nullable = false, length = 10)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_airport_id", nullable = false)
    private Airport originAirport;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_airport_id", nullable = false)
    private Airport destinationAirport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_id")
    private Aircraft aircraft;

    @Column(name = "scheduled_departure_time", nullable = false)
    private OffsetDateTime scheduledDepartureTime;

    @Column(name = "scheduled_arrival_time", nullable = false)
    private OffsetDateTime scheduledArrivalTime;

    @Column(name = "actual_departure_time")
    private OffsetDateTime actualDepartureTime;

    @Column(name = "actual_arrival_time")
    private OffsetDateTime actualArrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlightStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Flight() {
    }

    public Flight(String flightNumber, Airport originAirport, Airport destinationAirport, Aircraft aircraft,
                  OffsetDateTime scheduledDepartureTime, OffsetDateTime scheduledArrivalTime,
                  OffsetDateTime actualDepartureTime, OffsetDateTime actualArrivalTime, FlightStatus status) {
        this.flightNumber = flightNumber;
        this.originAirport = originAirport;
        this.destinationAirport = destinationAirport;
        this.aircraft = aircraft;
        this.scheduledDepartureTime = scheduledDepartureTime;
        this.scheduledArrivalTime = scheduledArrivalTime;
        this.actualDepartureTime = actualDepartureTime;
        this.actualArrivalTime = actualArrivalTime;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public Airport getOriginAirport() {
        return originAirport;
    }

    public Airport getDestinationAirport() {
        return destinationAirport;
    }

    public Aircraft getAircraft() {
        return aircraft;
    }

    public OffsetDateTime getScheduledDepartureTime() {
        return scheduledDepartureTime;
    }

    public OffsetDateTime getScheduledArrivalTime() {
        return scheduledArrivalTime;
    }

    public OffsetDateTime getActualDepartureTime() {
        return actualDepartureTime;
    }

    public OffsetDateTime getActualArrivalTime() {
        return actualArrivalTime;
    }

    public FlightStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
