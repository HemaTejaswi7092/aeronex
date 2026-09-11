package com.aeronex.disruption;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.aeronex.flight.Flight;

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
@Table(name = "disruptions")
public class Disruption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisruptionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisruptionSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisruptionStatus status;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "estimated_delay_minutes")
    private Integer estimatedDelayMinutes;

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Disruption() {
    }

    public Disruption(Flight flight, DisruptionType type, DisruptionSeverity severity, DisruptionStatus status,
                       String description, Integer estimatedDelayMinutes, OffsetDateTime reportedAt,
                       OffsetDateTime resolvedAt) {
        this.flight = flight;
        this.type = type;
        this.severity = severity;
        this.status = status;
        this.description = description;
        this.estimatedDelayMinutes = estimatedDelayMinutes;
        this.reportedAt = reportedAt;
        this.resolvedAt = resolvedAt;
    }

    public void resolve(OffsetDateTime resolvedAt) {
        this.status = DisruptionStatus.RESOLVED;
        if (this.resolvedAt == null) {
            this.resolvedAt = resolvedAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public Flight getFlight() {
        return flight;
    }

    public DisruptionType getType() {
        return type;
    }

    public DisruptionSeverity getSeverity() {
        return severity;
    }

    public DisruptionStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Integer getEstimatedDelayMinutes() {
        return estimatedDelayMinutes;
    }

    public OffsetDateTime getReportedAt() {
        return reportedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
