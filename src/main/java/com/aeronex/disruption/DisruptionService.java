package com.aeronex.disruption;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aeronex.disruption.dto.DisruptionCreateRequest;
import com.aeronex.disruption.dto.DisruptionResponse;
import com.aeronex.disruption.event.FlightDisruptionReportedPayload;
import com.aeronex.disruption.event.FlightDisruptionResolvedPayload;
import com.aeronex.disruption.exception.DisruptionNotFoundException;
import com.aeronex.disruption.exception.FlightNotEligibleForDisruptionException;
import com.aeronex.disruption.exception.InvalidDisruptionException;
import com.aeronex.eventing.DomainEventPublisher;
import com.aeronex.eventing.kafka.KafkaTopics;
import com.aeronex.flight.Flight;
import com.aeronex.flight.FlightRepository;
import com.aeronex.flight.FlightStatus;
import com.aeronex.flight.exception.FlightNotFoundException;

@Service
@Transactional(readOnly = true)
public class DisruptionService {

    private final DisruptionRepository disruptionRepository;
    private final FlightRepository flightRepository;
    private final DomainEventPublisher domainEventPublisher;

    public DisruptionService(DisruptionRepository disruptionRepository, FlightRepository flightRepository,
                              DomainEventPublisher domainEventPublisher) {
        this.disruptionRepository = disruptionRepository;
        this.flightRepository = flightRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public DisruptionResponse create(DisruptionCreateRequest request) {
        Flight flight = flightRepository.findById(request.flightId())
                .orElseThrow(() -> FlightNotFoundException.forId(request.flightId()));

        if (flight.getStatus() == FlightStatus.ARRIVED || flight.getStatus() == FlightStatus.CANCELLED) {
            throw new FlightNotEligibleForDisruptionException(
                    "Cannot create a disruption for flight " + flight.getFlightNumber()
                            + " because its status is " + flight.getStatus());
        }

        OffsetDateTime reportedAt = request.reportedAt() != null ? request.reportedAt() : OffsetDateTime.now();
        DisruptionStatus status = request.status() != null ? request.status() : DisruptionStatus.OPEN;
        OffsetDateTime resolvedAt = request.resolvedAt();

        if (resolvedAt != null && status != DisruptionStatus.RESOLVED) {
            throw new InvalidDisruptionException("resolvedAt may only be set when status is RESOLVED");
        }

        if (status == DisruptionStatus.RESOLVED && resolvedAt == null) {
            resolvedAt = OffsetDateTime.now();
        }

        if (resolvedAt != null && resolvedAt.isBefore(reportedAt)) {
            throw new InvalidDisruptionException("resolvedAt cannot be before reportedAt");
        }

        Disruption disruption = new Disruption(
                flight,
                request.type(),
                request.severity(),
                status,
                request.description(),
                request.estimatedDelayMinutes(),
                reportedAt,
                resolvedAt
        );

        Disruption saved = disruptionRepository.save(disruption);

        publishReportedEvent(saved);
        if (saved.getStatus() == DisruptionStatus.RESOLVED) {
            publishResolvedEvent(saved);
        }

        return DisruptionMapper.toResponse(saved);
    }

    public List<DisruptionResponse> findAll() {
        return disruptionRepository.findAllOrderByReportedAtDesc().stream()
                .map(DisruptionMapper::toResponse)
                .toList();
    }

    public DisruptionResponse findById(UUID id) {
        return disruptionRepository.findById(id)
                .map(DisruptionMapper::toResponse)
                .orElseThrow(() -> DisruptionNotFoundException.forId(id));
    }

    public List<DisruptionResponse> findByFlight(UUID flightId) {
        return disruptionRepository.findByFlightIdOrderByReportedAtDesc(flightId).stream()
                .map(DisruptionMapper::toResponse)
                .toList();
    }

    public List<DisruptionResponse> findActive() {
        return disruptionRepository.findByStatusInOrderByReportedAtDesc(
                        List.of(DisruptionStatus.OPEN, DisruptionStatus.MONITORING)).stream()
                .map(DisruptionMapper::toResponse)
                .toList();
    }

    @Transactional
    public DisruptionResponse resolve(UUID id) {
        Disruption disruption = disruptionRepository.findById(id)
                .orElseThrow(() -> DisruptionNotFoundException.forId(id));

        boolean wasAlreadyResolved = disruption.getStatus() == DisruptionStatus.RESOLVED;
        disruption.resolve(OffsetDateTime.now());

        Disruption saved = disruptionRepository.save(disruption);

        if (!wasAlreadyResolved) {
            publishResolvedEvent(saved);
        }

        return DisruptionMapper.toResponse(saved);
    }

    private void publishReportedEvent(Disruption disruption) {
        Flight flight = disruption.getFlight();
        FlightDisruptionReportedPayload payload = new FlightDisruptionReportedPayload(
                disruption.getId(),
                flight.getId(),
                flight.getFlightNumber(),
                flight.getOriginAirport().getIataCode(),
                flight.getDestinationAirport().getIataCode(),
                flight.getScheduledDepartureTime(),
                disruption.getType(),
                disruption.getSeverity(),
                disruption.getStatus(),
                disruption.getDescription(),
                disruption.getEstimatedDelayMinutes(),
                disruption.getReportedAt()
        );
        domainEventPublisher.publish(KafkaTopics.DISRUPTION_REPORTED, disruption.getId(),
                "FlightDisruptionReported", 1, payload);
    }

    private void publishResolvedEvent(Disruption disruption) {
        Flight flight = disruption.getFlight();
        long resolutionDurationMinutes = Duration.between(disruption.getReportedAt(), disruption.getResolvedAt())
                .toMinutes();
        FlightDisruptionResolvedPayload payload = new FlightDisruptionResolvedPayload(
                disruption.getId(),
                flight.getId(),
                flight.getFlightNumber(),
                disruption.getReportedAt(),
                disruption.getResolvedAt(),
                resolutionDurationMinutes
        );
        domainEventPublisher.publish(KafkaTopics.DISRUPTION_RESOLVED, disruption.getId(),
                "FlightDisruptionResolved", 1, payload);
    }
}
