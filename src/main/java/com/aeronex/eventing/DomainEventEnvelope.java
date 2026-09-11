package com.aeronex.eventing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DomainEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        UUID aggregateId,
        T payload
) {
}
