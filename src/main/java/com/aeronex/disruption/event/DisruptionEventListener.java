package com.aeronex.disruption.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.aeronex.eventing.kafka.KafkaTopics;

@Component
public class DisruptionEventListener {

    private final DisruptionEventProcessor disruptionEventProcessor;

    public DisruptionEventListener(DisruptionEventProcessor disruptionEventProcessor) {
        this.disruptionEventProcessor = disruptionEventProcessor;
    }

    @KafkaListener(topics = KafkaTopics.DISRUPTION_REPORTED, groupId = "${spring.kafka.consumer.group-id}")
    public void onDisruptionReported(String payload, Acknowledgment acknowledgment) {
        disruptionEventProcessor.process(payload);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = KafkaTopics.DISRUPTION_RESOLVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onDisruptionResolved(String payload, Acknowledgment acknowledgment) {
        disruptionEventProcessor.process(payload);
        acknowledgment.acknowledge();
    }
}
