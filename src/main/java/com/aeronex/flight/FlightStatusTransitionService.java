package com.aeronex.flight;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.aeronex.eventing.DomainEventPublisher;
import com.aeronex.eventing.kafka.KafkaTopics;
import com.aeronex.flight.event.FlightStatusChangedPayload;

/**
 * The single authority for flight lifecycle state changes. Both operator-requested
 * transitions (FlightService) and automatic disruption-driven transitions
 * (DisruptionService) go through {@link #apply}, so the flight status mutation,
 * history record, and outbox event are always created together or not at all.
 */
@Component
public class FlightStatusTransitionService {

    private final FlightRepository flightRepository;
    private final FlightStatusHistoryRepository flightStatusHistoryRepository;
    private final DomainEventPublisher domainEventPublisher;

    public FlightStatusTransitionService(FlightRepository flightRepository,
                                          FlightStatusHistoryRepository flightStatusHistoryRepository,
                                          DomainEventPublisher domainEventPublisher) {
        this.flightRepository = flightRepository;
        this.flightStatusHistoryRepository = flightStatusHistoryRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Validates and applies a transition, atomically (within the caller's transaction)
     * saving the flight, recording history, and publishing a FlightStatusChanged event.
     * Throws InvalidFlightStatusTransitionException and persists nothing if the
     * transition is illegal — callers should check {@link Flight#canTransitionTo}
     * first when the transition is conditional (e.g. disruption-driven) rather than
     * mandatory (e.g. operator-requested).
     */
    public Flight apply(Flight flight, FlightStatus target, TransitionSource source, String reason) {
        FlightStatus previousStatus = flight.getStatus();
        flight.transitionTo(target);

        Flight saved = flightRepository.saveAndFlush(flight);

        OffsetDateTime changedAt = OffsetDateTime.now();
        flightStatusHistoryRepository.saveAndFlush(
                new FlightStatusHistory(saved, previousStatus, target, source, reason, changedAt));

        domainEventPublisher.publish(KafkaTopics.FLIGHT_STATUS_CHANGED, saved.getId(), "FlightStatusChanged", 1,
                new FlightStatusChangedPayload(saved.getId(), saved.getFlightNumber(), previousStatus, target,
                        source, reason, changedAt));

        return saved;
    }

    /**
     * Seeds the audit trail with the flight's initial status at creation time. This is
     * not a transition (nothing changed from a prior state), so it deliberately does
     * NOT publish FlightStatusChanged — that event represents an actual transition.
     */
    public void seedInitialHistory(Flight flight) {
        flightStatusHistoryRepository.saveAndFlush(
                new FlightStatusHistory(flight, null, flight.getStatus(), TransitionSource.CREATED, null,
                        OffsetDateTime.now()));
    }
}
