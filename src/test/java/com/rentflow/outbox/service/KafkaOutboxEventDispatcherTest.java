package com.rentflow.outbox.service;

import com.rentflow.outbox.config.OutboxKafkaProperties;
import com.rentflow.outbox.entity.OutboxEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxEventDispatcherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private OutboxKafkaRecordFactory recordFactory;

    private OutboxKafkaProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private KafkaOutboxEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new OutboxKafkaProperties();
        properties.setTopic("rentflow.outbox.v1");
        properties.setSendTimeout(Duration.ofMillis(25));
        meterRegistry = new SimpleMeterRegistry();
        dispatcher = new KafkaOutboxEventDispatcher(kafkaTemplate, recordFactory, properties, meterRegistry);
    }

    @Test
    void dispatchWaitsForBrokerAckAndRecordsSuccess() {
        OutboxEvent event = event();
        ProducerRecord<String, String> record = new ProducerRecord<>("rentflow.outbox.v1", "booking-1", "{}");
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("rentflow.outbox.v1", 2),
                42L,
                0,
                0L,
                0,
                0);
        when(recordFactory.createRecord(event)).thenReturn(record);
        when(kafkaTemplate.send(record)).thenReturn(CompletableFuture.completedFuture(new SendResult<>(record, metadata)));

        dispatcher.dispatch(event);

        assertThat(meterRegistry.counter("rentflow.outbox.kafka.sent").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("rentflow.outbox.kafka.failed").count()).isZero();
        assertThat(meterRegistry.timer("rentflow.outbox.kafka.send.latency").count()).isEqualTo(1);
    }

    @Test
    void dispatchThrowsWhenSendFutureFails() {
        OutboxEvent event = event();
        ProducerRecord<String, String> record = new ProducerRecord<>("rentflow.outbox.v1", "booking-1", "{}");
        when(recordFactory.createRecord(event)).thenReturn(record);
        when(kafkaTemplate.send(record)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> dispatcher.dispatch(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kafka outbox dispatch failed");

        assertThat(meterRegistry.counter("rentflow.outbox.kafka.sent").count()).isZero();
        assertThat(meterRegistry.counter("rentflow.outbox.kafka.failed").count()).isEqualTo(1);
    }

    @Test
    void dispatchThrowsWhenSendTimesOut() {
        OutboxEvent event = event();
        ProducerRecord<String, String> record = new ProducerRecord<>("rentflow.outbox.v1", "booking-1", "{}");
        when(recordFactory.createRecord(event)).thenReturn(record);
        when(kafkaTemplate.send(record)).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> dispatcher.dispatch(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kafka outbox dispatch failed");

        assertThat(meterRegistry.counter("rentflow.outbox.kafka.sent").count()).isZero();
        assertThat(meterRegistry.counter("rentflow.outbox.kafka.failed").count()).isEqualTo(1);
    }

    private OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType("BOOKING");
        event.setEventType("BOOKING_HELD");
        return event;
    }
}
