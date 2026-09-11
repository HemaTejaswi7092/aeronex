package com.aeronex.eventing.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {

    @Mock
    private Admin admin;

    @Mock
    private DescribeClusterResult describeClusterResult;

    @Mock
    private KafkaFuture<String> clusterIdFuture;

    @Test
    void reportsUpWhenClusterIsReachable() throws Exception {
        when(admin.describeCluster()).thenReturn(describeClusterResult);
        when(describeClusterResult.clusterId()).thenReturn(clusterIdFuture);
        when(clusterIdFuture.get(2, TimeUnit.SECONDS)).thenReturn("test-cluster-id");

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(() -> admin);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenClusterIsUnreachable() throws Exception {
        when(admin.describeCluster()).thenReturn(describeClusterResult);
        when(describeClusterResult.clusterId()).thenReturn(clusterIdFuture);
        when(clusterIdFuture.get(2, TimeUnit.SECONDS))
                .thenThrow(new ExecutionException("broker unavailable", null));

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(() -> admin);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
