package com.aeronex.eventing.kafka;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String DISRUPTION_REPORTED = "aeronex.disruption.reported";
    public static final String DISRUPTION_RESOLVED = "aeronex.disruption.resolved";
    public static final String FLIGHT_STATUS_CHANGED = "aeronex.flight.status-changed";
}
