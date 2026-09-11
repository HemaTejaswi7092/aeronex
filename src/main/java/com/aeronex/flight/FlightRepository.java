package com.aeronex.flight;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightRepository extends JpaRepository<Flight, UUID> {

    @Override
    @EntityGraph(attributePaths = {"originAirport", "destinationAirport", "aircraft"})
    Optional<Flight> findById(UUID id);

    @EntityGraph(attributePaths = {"originAirport", "destinationAirport", "aircraft"})
    @Query("SELECT f FROM Flight f ORDER BY f.scheduledDepartureTime ASC")
    List<Flight> findAllOrderByScheduledDepartureTime();

    @EntityGraph(attributePaths = {"originAirport", "destinationAirport", "aircraft"})
    List<Flight> findByFlightNumberOrderByScheduledDepartureTimeAsc(String flightNumber);

    @EntityGraph(attributePaths = {"originAirport", "destinationAirport", "aircraft"})
    @Query("SELECT f FROM Flight f WHERE f.originAirport.iataCode = :iataCode "
            + "OR f.destinationAirport.iataCode = :iataCode ORDER BY f.scheduledDepartureTime ASC")
    List<Flight> findByOriginOrDestinationIataCode(@Param("iataCode") String iataCode);
}
