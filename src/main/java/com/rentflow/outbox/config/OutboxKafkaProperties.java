package com.rentflow.outbox.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "rentflow.outbox.kafka")
public class OutboxKafkaProperties {

    private String topic = "rentflow.outbox.v1";
    private Duration sendTimeout = Duration.ofSeconds(5);
    private boolean autoCreateTopic = true;
    private int partitions = 6;
    private int replicationFactor = 1;

    @PostConstruct
    void validate() {
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("rentflow.outbox.kafka.topic must not be blank");
        }
        if (sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()) {
            throw new IllegalStateException("rentflow.outbox.kafka.send-timeout must be positive");
        }
        if (partitions < 1) {
            throw new IllegalStateException("rentflow.outbox.kafka.partitions must be at least 1");
        }
        if (replicationFactor < 1) {
            throw new IllegalStateException("rentflow.outbox.kafka.replication-factor must be at least 1");
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic == null ? null : topic.trim();
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public boolean isAutoCreateTopic() {
        return autoCreateTopic;
    }

    public void setAutoCreateTopic(boolean autoCreateTopic) {
        this.autoCreateTopic = autoCreateTopic;
    }

    public int getPartitions() {
        return partitions;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }
}
