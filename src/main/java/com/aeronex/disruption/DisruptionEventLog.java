package com.aeronex.disruption;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "disruption_event_log")
public class DisruptionEventLog {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "disruption_id", nullable = false)
    private UUID disruptionId;

    @Column(name = "flight_id", nullable = false)
    private UUID flightId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected DisruptionEventLog() {
    }

    public DisruptionEventLog(UUID id, UUID eventId, UUID disruptionId, UUID flightId, String eventType,
                               OffsetDateTime occurredAt, String summary, Instant recordedAt) {
        this.id = id;
        this.eventId = eventId;
        this.disruptionId = disruptionId;
        this.flightId = flightId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.summary = summary;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getDisruptionId() {
        return disruptionId;
    }

    public UUID getFlightId() {
        return flightId;
    }

    public String getEventType() {
        return eventType;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
