package com.aeronex.flight;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightStatusHistoryRepository extends JpaRepository<FlightStatusHistory, UUID> {

    List<FlightStatusHistory> findByFlightIdOrderByChangedAtAsc(UUID flightId);
}
