package com.aeronex.aircraft;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.aeronex.aircraft.event.AircraftStatusChangedPayload;
import com.aeronex.eventing.DomainEventPublisher;
import com.aeronex.eventing.kafka.KafkaTopics;

/**
 * The single authority for aircraft lifecycle state changes. Both operator-requested
 * transitions (AircraftService) and automatic disruption-driven transitions
 * (DisruptionService) go through {@link #apply}, so the aircraft status mutation,
 * history record, and outbox event are always created together or not at all.
 */
@Component
public class AircraftStatusTransitionService {

    private final AircraftRepository aircraftRepository;
    private final AircraftStatusHistoryRepository aircraftStatusHistoryRepository;
    private final DomainEventPublisher domainEventPublisher;

    public AircraftStatusTransitionService(AircraftRepository aircraftRepository,
                                            AircraftStatusHistoryRepository aircraftStatusHistoryRepository,
                                            DomainEventPublisher domainEventPublisher) {
        this.aircraftRepository = aircraftRepository;
        this.aircraftStatusHistoryRepository = aircraftStatusHistoryRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Validates and applies a transition, atomically (within the caller's transaction)
     * saving the aircraft, recording history, and publishing an AircraftStatusChanged
     * event. Throws InvalidAircraftStatusTransitionException and persists nothing if
     * the transition is illegal — callers should check {@link Aircraft#canTransitionTo}
     * first when the transition is conditional (e.g. disruption-driven) rather than
     * mandatory (e.g. operator-requested).
     */
    public Aircraft apply(Aircraft aircraft, AircraftStatus target, AircraftTransitionSource source, String reason) {
        AircraftStatus previousStatus = aircraft.getStatus();
        aircraft.transitionTo(target);

        Aircraft saved = aircraftRepository.saveAndFlush(aircraft);

        OffsetDateTime changedAt = OffsetDateTime.now();
        aircraftStatusHistoryRepository.saveAndFlush(
                new AircraftStatusHistory(saved, previousStatus, target, source, reason, changedAt));

        domainEventPublisher.publish(KafkaTopics.AIRCRAFT_STATUS_CHANGED, saved.getId(), "AircraftStatusChanged", 1,
                new AircraftStatusChangedPayload(saved.getId(), saved.getRegistrationNumber(), previousStatus, target,
                        source, reason, changedAt));

        return saved;
    }

    /**
     * Seeds the audit trail with the aircraft's initial status at creation time. This
     * is not a transition (nothing changed from a prior state), so it deliberately
     * does NOT publish AircraftStatusChanged — that event represents an actual
     * transition.
     */
    public void seedInitialHistory(Aircraft aircraft) {
        aircraftStatusHistoryRepository.saveAndFlush(
                new AircraftStatusHistory(aircraft, null, aircraft.getStatus(), AircraftTransitionSource.CREATED,
                        null, OffsetDateTime.now()));
    }
}
