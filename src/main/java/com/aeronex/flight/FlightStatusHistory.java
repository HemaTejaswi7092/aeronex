package com.aeronex.flight;

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
@Table(name = "flight_status_history")
public class FlightStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private FlightStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private FlightStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransitionSource source;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    protected FlightStatusHistory() {
    }

    public FlightStatusHistory(Flight flight, FlightStatus previousStatus, FlightStatus newStatus,
                                TransitionSource source, String reason, OffsetDateTime changedAt) {
        this.flight = flight;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.source = source;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public Flight getFlight() {
        return flight;
    }

    public FlightStatus getPreviousStatus() {
        return previousStatus;
    }

    public FlightStatus getNewStatus() {
        return newStatus;
    }

    public TransitionSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }
}
