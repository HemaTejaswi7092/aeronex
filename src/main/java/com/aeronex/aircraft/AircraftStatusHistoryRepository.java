package com.aeronex.aircraft;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftStatusHistoryRepository extends JpaRepository<AircraftStatusHistory, UUID> {

    List<AircraftStatusHistory> findByAircraftIdOrderByChangedAtAsc(UUID aircraftId);
}
