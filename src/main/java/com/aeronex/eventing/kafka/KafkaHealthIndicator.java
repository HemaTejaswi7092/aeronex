package com.aeronex.eventing.kafka;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Reports Kafka broker reachability via the admin client. Boot ships no built-in Kafka
 * health indicator, so this fills that gap using the auto-configured {@link KafkaAdmin} bean.
 *
 * <p>Gated by the same flag as {@link com.aeronex.eventing.outbox.OutboxRelay} since that
 * flag already means "this instance actively participates in Kafka" — this keeps the
 * standard test suite from depending on a reachable broker at localhost:9092, exactly as
 * the relay's own conditional does.
 */
@Component
@ConditionalOnProperty(name = "aeronex.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaHealthIndicator implements HealthIndicator {

    private final Supplier<Admin> adminSupplier;

    @Autowired
    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this(() -> Admin.create(kafkaAdmin.getConfigurationProperties()));
    }

    KafkaHealthIndicator(Supplier<Admin> adminSupplier) {
        this.adminSupplier = adminSupplier;
    }

    @Override
    public Health health() {
        try (Admin admin = adminSupplier.get()) {
            String clusterId = admin.describeCluster().clusterId().get(2, TimeUnit.SECONDS);
            return Health.up().withDetail("clusterId", clusterId).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
