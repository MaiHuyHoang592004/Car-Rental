package com.rentflow.integration.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.outbox.entity.OutboxEvent;
import com.rentflow.outbox.repository.OutboxEventRepository;
import com.rentflow.outbox.service.OutboxPublisherService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class KafkaOutboxPublisherIntegrationTest extends BaseIntegrationTest {

    private static final String TOPIC = "rentflow.outbox.v1";

    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    static {
        kafka.start();
    }

    @DynamicPropertySource
    static void configureKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("rentflow.outbox.dispatcher.type", () -> "kafka");
        registry.add("rentflow.outbox.kafka.topic", () -> TOPIC);
        registry.add("rentflow.outbox.kafka.auto-create-topic", () -> true);
        registry.add("rentflow.outbox.kafka.partitions", () -> 1);
        registry.add("rentflow.outbox.kafka.replication-factor", () -> 1);
        registry.add("rentflow.outbox.kafka.send-timeout", () -> "PT10S");
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearOutboxEvents() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void processBatchPublishesOutboxEventToKafkaAndMarksSent() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("BOOKING");
        event.setAggregateId(aggregateId);
        event.setEventType("BOOKING_HELD");
        event.setPayload("{\"bookingId\":\"bk-1\",\"status\":\"HELD\"}");
        event.setStatus("PENDING");
        OutboxEvent saved = outboxEventRepository.save(event);

        outboxPublisherService.processBatch(10, 5, 1, 300);

        OutboxEvent sent = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo("SENT");
        assertThat(sent.getSentAt()).isNotNull();

        ConsumerRecord<String, String> record = consumeSingleRecord();
        assertThat(record.key()).isEqualTo(aggregateId.toString());

        JsonNode envelope = objectMapper.readTree(record.value());
        assertThat(envelope.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.path("eventId").asText()).isEqualTo(saved.getId().toString());
        assertThat(envelope.path("aggregateType").asText()).isEqualTo("BOOKING");
        assertThat(envelope.path("aggregateId").asText()).isEqualTo(aggregateId.toString());
        assertThat(envelope.path("eventType").asText()).isEqualTo("BOOKING_HELD");
        assertThat(envelope.path("payload").path("bookingId").asText()).isEqualTo("bk-1");
    }

    private ConsumerRecord<String, String> consumeSingleRecord() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "rentflow-outbox-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    return record;
                }
            }
        }
        throw new AssertionError("No Kafka record received from " + TOPIC);
    }
}
