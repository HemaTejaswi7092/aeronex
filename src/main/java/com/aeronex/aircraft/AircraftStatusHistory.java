package com.aeronex.aircraft;

import java.time.OffsetDateTime;
import java.util.UUID;

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
@Table(name = "aircraft_status_history")
public class AircraftStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private AircraftStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private AircraftStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AircraftTransitionSource source;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    protected AircraftStatusHistory() {
    }

    public AircraftStatusHistory(Aircraft aircraft, AircraftStatus previousStatus, AircraftStatus newStatus,
                                  AircraftTransitionSource source, String reason, OffsetDateTime changedAt) {
        this.aircraft = aircraft;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.source = source;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public Aircraft getAircraft() {
        return aircraft;
    }

    public AircraftStatus getPreviousStatus() {
        return previousStatus;
    }

    public AircraftStatus getNewStatus() {
        return newStatus;
    }

    public AircraftTransitionSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }
}
