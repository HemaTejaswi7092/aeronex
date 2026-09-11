package com.aeronex.disruption;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DisruptionEventLogRepository extends JpaRepository<DisruptionEventLog, UUID> {

    List<DisruptionEventLog> findByDisruptionIdOrderByOccurredAtAsc(UUID disruptionId);
}
