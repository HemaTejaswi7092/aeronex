package com.aeronex.disruption;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DisruptionRepository extends JpaRepository<Disruption, UUID> {

    @Override
    @EntityGraph(attributePaths = {"flight", "flight.originAirport", "flight.destinationAirport"})
    Optional<Disruption> findById(UUID id);

    @EntityGraph(attributePaths = {"flight", "flight.originAirport", "flight.destinationAirport"})
    @Query("SELECT d FROM Disruption d ORDER BY d.reportedAt DESC")
    List<Disruption> findAllOrderByReportedAtDesc();

    @EntityGraph(attributePaths = {"flight", "flight.originAirport", "flight.destinationAirport"})
    List<Disruption> findByFlightIdOrderByReportedAtDesc(UUID flightId);

    @EntityGraph(attributePaths = {"flight", "flight.originAirport", "flight.destinationAirport"})
    List<Disruption> findByStatusInOrderByReportedAtDesc(Collection<DisruptionStatus> statuses);
}
