package com.rentflow.outbox.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "rentflow.outbox.dispatcher.type", havingValue = "kafka")
public class OutboxKafkaConfiguration {

    private final Environment environment;

    public OutboxKafkaConfiguration(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateBootstrapServers() {
        String bootstrapServers = environment.getProperty("spring.kafka.bootstrap-servers");
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalStateException(
                    "spring.kafka.bootstrap-servers must be configured when rentflow.outbox.dispatcher.type=kafka");
        }
    }

    @Bean
    @ConditionalOnProperty(
            name = "rentflow.outbox.kafka.auto-create-topic",
            havingValue = "true",
            matchIfMissing = true)
    NewTopic rentflowOutboxTopic(OutboxKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopic())
                .partitions(properties.getPartitions())
                .replicas(properties.getReplicationFactor())
                .build();
    }
}
