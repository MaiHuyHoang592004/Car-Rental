package com.rentflow.outbox.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxKafkaConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OutboxKafkaConfiguration.class, OutboxKafkaProperties.class);

    @Test
    void kafkaDispatcherRequiresBootstrapServers() {
        contextRunner
                .withPropertyValues("rentflow.outbox.dispatcher.type=kafka")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable rootCause = rootCause(context.getStartupFailure());
                    assertThat(rootCause)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("spring.kafka.bootstrap-servers");
                });
    }

    @Test
    void createsTopicWhenAutoCreateTopicIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "rentflow.outbox.dispatcher.type=kafka",
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "rentflow.outbox.kafka.topic=rentflow.outbox.v1",
                        "rentflow.outbox.kafka.partitions=6",
                        "rentflow.outbox.kafka.replication-factor=1",
                        "rentflow.outbox.kafka.auto-create-topic=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    NewTopic topic = context.getBean(NewTopic.class);
                    assertThat(topic.name()).isEqualTo("rentflow.outbox.v1");
                    assertThat(topic.numPartitions()).isEqualTo(6);
                    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
                });
    }

    @Test
    void skipsTopicBeanWhenAutoCreateTopicIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "rentflow.outbox.dispatcher.type=kafka",
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "rentflow.outbox.kafka.auto-create-topic=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NewTopic.class);
                });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
